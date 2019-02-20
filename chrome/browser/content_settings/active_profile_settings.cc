#include "chrome/browser/content_settings/host_content_settings_map_factory.h"
#include "chrome/browser/profiles/profile_manager.h"
#include "components/content_settings/core/browser/host_content_settings_map.h"

namespace content {

bool IsGlobalDesktopSettingsOnForActiveProfile() {
  bool is_desktop_settings_on = HostContentSettingsMapFactory::GetForProfile(
    ProfileManager::GetActiveUserProfile()->GetOriginalProfile())->
    GetDefaultContentSetting(CONTENT_SETTINGS_TYPE_DESKTOP_VIEW, NULL) == CONTENT_SETTING_ALLOW;
  return is_desktop_settings_on;
}

bool NeedPlayVideoInBackgroundForActiveProfile() {
  bool play_video_in_background_enabled = HostContentSettingsMapFactory::GetForProfile(
      ProfileManager::GetActiveUserProfile()->GetOriginalProfile())->
      GetDefaultContentSetting(CONTENT_SETTINGS_TYPE_PLAY_VIDEO_IN_BACKGROUND, NULL) == CONTENT_SETTING_ALLOW;
  return play_video_in_background_enabled;
}

}
