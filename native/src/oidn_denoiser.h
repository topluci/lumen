#pragma once
#include <cstdint>
#include <cstdio>

#ifdef _WIN32
#include <windows.h>
#endif

class OidnDenoiser {
public:
    OidnDenoiser() = default;
    ~OidnDenoiser() { shutdown(); }

    bool init(int width, int height, bool useGPU);
    bool denoise(uint32_t* pixels, int width, int height);
    void shutdown();
    bool isReady() const { return m_ready; }
    const char* lastError() const { return m_lastError; }

private:
    bool loadLibrary();

    // OIDN opaque types
    struct OIDNDeviceImpl; using OIDNDevice = OIDNDeviceImpl*;
    struct OIDNFilterImpl; using OIDNFilter = OIDNFilterImpl*;

    // OIDN constants
    enum { OIDN_DEVICE_TYPE_DEFAULT = 0, OIDN_DEVICE_TYPE_CPU = 1, OIDN_DEVICE_TYPE_HIP = 5 };
    enum { OIDN_FORMAT_FLOAT3 = 0x2002, OIDN_FORMAT_FLOAT = 0x2000 };
    enum { OIDN_ACCESS_READ_WRITE = 4 };
    enum { OIDN_FILTER_RT = 0x4000 };

    // Function pointer types
    using FP_oidnNewDevice = OIDNDevice(*)(int);
    using FP_oidnGetDeviceError = const char*(*)(OIDNDevice, int*);
    using FP_oidnCommitDevice = void(*)(OIDNDevice);
    using FP_oidnReleaseDevice = void(*)(OIDNDevice);
    using FP_oidnNewFilter = OIDNFilter(*)(OIDNDevice, const char*);
    using FP_oidnSetSharedFilterImage = void(*)(OIDNFilter, const char*, void*, int, size_t, size_t, size_t, size_t, size_t);
    using FP_oidnSetFilterBool = void(*)(OIDNFilter, const char*, bool);
    using FP_oidnSetFilterInt = void(*)(OIDNFilter, const char*, int);
    using FP_oidnCommitFilter = void(*)(OIDNFilter);
    using FP_oidnExecuteFilter = void(*)(OIDNFilter);
    using FP_oidnReleaseFilter = void(*)(OIDNFilter);

#ifdef _WIN32
    HMODULE m_lib = nullptr;
#endif
    FP_oidnNewDevice m_oidnNewDevice = nullptr;
    FP_oidnGetDeviceError m_oidnGetDeviceError = nullptr;
    FP_oidnCommitDevice m_oidnCommitDevice = nullptr;
    FP_oidnReleaseDevice m_oidnReleaseDevice = nullptr;
    FP_oidnNewFilter m_oidnNewFilter = nullptr;
    FP_oidnSetSharedFilterImage m_oidnSetSharedFilterImage = nullptr;
    FP_oidnSetFilterBool m_oidnSetFilterBool = nullptr;
    FP_oidnSetFilterInt m_oidnSetFilterInt = nullptr;
    FP_oidnCommitFilter m_oidnCommitFilter = nullptr;
    FP_oidnExecuteFilter m_oidnExecuteFilter = nullptr;
    FP_oidnReleaseFilter m_oidnReleaseFilter = nullptr;

    OIDNDevice m_device = nullptr;
    OIDNFilter m_filter = nullptr;
    bool m_ready = false;

    float* m_floatBuf = nullptr;
    int m_bufSize = 0;
    int m_width = 0;
    int m_height = 0;

    char m_lastError[256] = {};
};
