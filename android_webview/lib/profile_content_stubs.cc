// TODO(alexeyb): remove this workaround for release monochrome_public_apk
namespace content {

bool IsGlobalDesktopSettingsOnForActiveProfile() {
    return false;
}

// [MV] //
bool IsDimmingOnForActiveProfile() {
    return false; 
}
////

bool NeedPlayVideoInBackgroundForActiveProfile() {
    return false;
}

}  // namespace content
