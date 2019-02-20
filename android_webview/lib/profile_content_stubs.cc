// TODO(alexeyb): remove this workaround for release monochrome_public_apk
namespace content {

bool IsGlobalDesktopSettingsOnForActiveProfile() {
  return false;
}

bool NeedPlayVideoInBackgroundForActiveProfile() {
    return false;
}

}  // namespace content
