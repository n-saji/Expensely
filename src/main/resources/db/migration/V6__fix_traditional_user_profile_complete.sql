-- Fix is_profile_complete for existing non-OAuth users with a phone number
UPDATE users
SET is_profile_complete = true
WHERE (is_oauth2_user = false OR is_oauth2_user IS NULL)
  AND phone IS NOT NULL
  AND phone != '';
