-- Remove the eight disabled business areas and all of their child menus.
-- Keep the separate BPM workflow menu (/bpm) and its OA leave example.
CREATE TEMPORARY TABLE disabled_business_menu_ids AS
WITH RECURSIVE menu_tree AS (
    SELECT id
    FROM system_menu
    WHERE id IN (480, 597, 1348, 959, 8200, 1476, 1894, 8000)
    UNION ALL
    SELECT child.id
    FROM system_menu child
    JOIN menu_tree parent ON child.parent_id = parent.id
)
SELECT DISTINCT id FROM menu_tree;

DELETE role_menu
FROM system_role_menu role_menu
JOIN disabled_business_menu_ids removed ON removed.id = role_menu.menu_id;

UPDATE system_menu menu
JOIN disabled_business_menu_ids removed ON removed.id = menu.id
SET menu.deleted = b'1', menu.status = 1, menu.visible = b'0';

DROP TEMPORARY TABLE disabled_business_menu_ids;
