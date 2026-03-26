ALTER TABLE telework_requests
  ADD COLUMN justification_reason VARCHAR(500),
  ADD COLUMN manager_comment VARCHAR(500),
  ADD COLUMN hr_comment VARCHAR(500);

UPDATE telework_requests
SET alfresco_node_id = REPLACE(alfresco_node_id, 'workspace://SpacesStore/', '')
WHERE alfresco_node_id LIKE 'workspace://SpacesStore/%';
