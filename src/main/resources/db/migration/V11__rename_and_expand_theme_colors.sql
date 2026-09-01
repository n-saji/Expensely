ALTER TABLE users
    ALTER COLUMN theme_color SET DEFAULT 'supabase';

UPDATE users
SET theme_color = CASE LOWER(TRIM(theme_color))
    WHEN 'teal' THEN 'supabase'
    WHEN 'blue' THEN 'twitter'
    WHEN 'indigo' THEN 'bold-tech'
    WHEN 'emerald' THEN 'nature'
    WHEN 'amber' THEN 'amber-minimal'
    WHEN 'rose' THEN 'cyberpunk'
    WHEN 'slate' THEN 'mono'
    WHEN 'violet' THEN 'violet-bloom'
    WHEN 'orange' THEN 'tangerine'
    WHEN 'crimson' THEN 't3-chat'
    WHEN 'forest' THEN 'kodama-grove'
    ELSE LOWER(TRIM(theme_color))
END;
