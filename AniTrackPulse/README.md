# AniTrackPulse

Updated native Android starter with the requested tweaks:
- Separate folder lists: Watching, Plan to Watch, Finished, Dropped
- Search add menu supports all four folders
- Top-right in-app notification bell with unread state, badge count, clear-all, per-item delete
- Release notifications stored in app list
- Finished anime still appear in calendar while a future episode is scheduled
- Finished anime disappear from calendar when no further episode is scheduled
- Room persistence, DataStore settings, Compose UI

Notes:
- Room uses fallbackToDestructiveMigration() because status enum changed to add FINISHED.
- API calls remain mocked in AniListApi.kt and should be replaced with the real AniList integration if you want production data.
