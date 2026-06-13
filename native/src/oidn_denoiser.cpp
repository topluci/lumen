#include "oidn_denoiser.h"
#include <cstring>

#ifdef _WIN32
static const char* OIDN_DLL_NAME = "OpenImageDenoise.dll";
#endif

bool OidnDenoiser::loadLibrary() {
#ifdef _WIN32
    if (m_lib) return true;

    m_lib = LoadLibraryA(OIDN_DLL_NAME);
    if (!m_lib) {
        DWORD err = GetLastError();
        snprintf(m_lastError, sizeof(m_lastError),
                 "Failed to load %s (error %lu)", OIDN_DLL_NAME, err);
        return false;
    }

    #define LOAD_OIDN_PROC(name, fp) \
        fp = (decltype(fp))(GetProcAddress(m_lib, name)); \
        if (!fp) { \
            snprintf(m_lastError, sizeof(m_lastError), \
                     "Missing OIDN proc: %s", name); \
            FreeLibrary(m_lib); m_lib = nullptr; \
            return false; \
        }

    LOAD_OIDN_PROC("oidnNewDevice", m_oidnNewDevice);
    LOAD_OIDN_PROC("oidnGetDeviceError", m_oidnGetDeviceError);
    LOAD_OIDN_PROC("oidnCommitDevice", m_oidnCommitDevice);
    LOAD_OIDN_PROC("oidnReleaseDevice", m_oidnReleaseDevice);
    LOAD_OIDN_PROC("oidnNewFilter", m_oidnNewFilter);
    LOAD_OIDN_PROC("oidnSetSharedFilterImage", m_oidnSetSharedFilterImage);
    LOAD_OIDN_PROC("oidnSetFilterBool", m_oidnSetFilterBool);
    LOAD_OIDN_PROC("oidnSetFilterInt", m_oidnSetFilterInt);
    LOAD_OIDN_PROC("oidnCommitFilter", m_oidnCommitFilter);
    LOAD_OIDN_PROC("oidnExecuteFilter", m_oidnExecuteFilter);
    LOAD_OIDN_PROC("oidnReleaseFilter", m_oidnReleaseFilter);

    #undef LOAD_OIDN_PROC

    return true;
#else
    snprintf(m_lastError, sizeof(m_lastError), "OIDN not supported on this platform");
    return false;
#endif
}

bool OidnDenoiser::init(int width, int height, bool useGPU) {
    shutdown();

    if (!loadLibrary()) return false;

    int deviceType = useGPU ? OIDN_DEVICE_TYPE_HIP : OIDN_DEVICE_TYPE_CPU;
    m_device = m_oidnNewDevice(deviceType);

    if (!m_device) {
        // Fallback to CPU if HIP failed
        printf("[OIDN] HIP device not available, falling back to CPU\n");
        m_device = m_oidnNewDevice(OIDN_DEVICE_TYPE_CPU);
    }

    if (!m_device) {
        snprintf(m_lastError, sizeof(m_lastError), "Failed to create OIDN device");
        return false;
    }

    m_oidnCommitDevice(m_device);

    // Allocate float buffer for color conversion (width * height * 3 floats)
    size_t floatCount = (size_t)width * height * 3;
    m_floatBuf = new float[floatCount];
    m_bufSize = (int)floatCount;
    m_width = width;
    m_height = height;

    // Create RT filter (color-only mode)
    m_filter = m_oidnNewFilter(m_device, "RT");
    if (!m_filter) {
        const char* err = m_oidnGetDeviceError(m_device, nullptr);
        snprintf(m_lastError, sizeof(m_lastError), "Failed to create RT filter: %s",
                 err ? err : "unknown");
        delete[] m_floatBuf; m_floatBuf = nullptr;
        return false;
    }

    // Set images
    m_oidnSetSharedFilterImage(m_filter, "color",
                                m_floatBuf, OIDN_FORMAT_FLOAT3,
                                (size_t)width, (size_t)height,
                                0, 0, 0);
    m_oidnSetSharedFilterImage(m_filter, "output",
                                m_floatBuf, OIDN_FORMAT_FLOAT3,
                                (size_t)width, (size_t)height,
                                0, 0, 0);

    // Color-only mode: set albedo and normal to empty
    // For now we pass empty float buffer for albedo/normal (no AOV support yet)
    float* dummy = m_floatBuf;
    m_oidnSetSharedFilterImage(m_filter, "albedo",
                                dummy, OIDN_FORMAT_FLOAT3,
                                (size_t)width, (size_t)height,
                                0, 0, 0);
    m_oidnSetSharedFilterImage(m_filter, "normal",
                                dummy, OIDN_FORMAT_FLOAT3,
                                (size_t)width, (size_t)height,
                                0, 0, 0);

    m_oidnSetFilterBool(m_filter, "hdr", true);
    m_oidnSetFilterInt(m_filter, "quality", 1); // Balanced quality

    m_oidnCommitFilter(m_filter);

    int errorCode = 0;
    const char* err = m_oidnGetDeviceError(m_device, &errorCode);
    if (err) {
        snprintf(m_lastError, sizeof(m_lastError), "OIDN init error: %s", err);
        delete[] m_floatBuf; m_floatBuf = nullptr;
        return false;
    }

    printf("[OIDN] Denoiser initialized (%dx%d, %s)\n",
           width, height, useGPU ? "HIP" : "CPU");
    m_ready = true;
    return true;
}

bool OidnDenoiser::denoise(uint32_t* pixels, int width, int height) {
    if (!m_ready || !m_filter) return false;

    if (width != m_width || height != m_height) {
        snprintf(m_lastError, sizeof(m_lastError),
                 "Dimension mismatch: expected %dx%d, got %dx%d",
                 m_width, m_height, width, height);
        return false;
    }

    int pixelCount = width * height;

    // Convert uint32 ARGB to float3 RGB
    for (int i = 0; i < pixelCount; i++) {
        uint32_t p = pixels[i];
        float r = (float)((p >> 16) & 0xFF) / 255.0f;
        float g = (float)((p >> 8) & 0xFF) / 255.0f;
        float b = (float)(p & 0xFF) / 255.0f;
        m_floatBuf[i * 3 + 0] = r;
        m_floatBuf[i * 3 + 1] = g;
        m_floatBuf[i * 3 + 2] = b;
    }

    // Execute denoiser
    m_oidnExecuteFilter(m_filter);

    int errorCode = 0;
    const char* err = m_oidnGetDeviceError(m_device, &errorCode);
    if (err) {
        snprintf(m_lastError, sizeof(m_lastError), "OIDN execute error: %s", err);
        return false;
    }

    // Convert float3 RGB back to uint32 ARGB
    for (int i = 0; i < pixelCount; i++) {
        float r = m_floatBuf[i * 3 + 0];
        float g = m_floatBuf[i * 3 + 1];
        float b = m_floatBuf[i * 3 + 2];
        // Clamp and convert
        uint32_t ri = (uint32_t)(r < 0.0f ? 0.0f : (r > 1.0f ? 1.0f : r) * 255.0f);
        uint32_t gi = (uint32_t)(g < 0.0f ? 0.0f : (g > 1.0f ? 1.0f : g) * 255.0f);
        uint32_t bi = (uint32_t)(b < 0.0f ? 0.0f : (b > 1.0f ? 1.0f : b) * 255.0f);
        pixels[i] = 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    return true;
}

void OidnDenoiser::shutdown() {
    m_ready = false;
    if (m_filter) { m_oidnReleaseFilter(m_filter); m_filter = nullptr; }
    if (m_device) { m_oidnReleaseDevice(m_device); m_device = nullptr; }
    delete[] m_floatBuf; m_floatBuf = nullptr;
    m_bufSize = 0;
#ifdef _WIN32
    if (m_lib) { FreeLibrary(m_lib); m_lib = nullptr; }
#endif
}
