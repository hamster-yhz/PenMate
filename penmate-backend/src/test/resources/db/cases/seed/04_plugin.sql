-- Plugin configuration used by explicit demo tests.
INSERT INTO plugin_catalog(plugin_id, code, name, category, provider, status, latest_version)
VALUES (920931, 'demo-plot-review', 'Demo Plot Review', 'writing', 'penmate', 'active', '1.0.0')
ON CONFLICT (plugin_id) DO UPDATE SET
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO plugin_project_installs(
    plugin_install_id, project_id, plugin_id, version, config_json, enabled, installed_by)
VALUES (920941, 920001, 920931, '1.0.0', '{"mode":"strict"}'::jsonb, TRUE, 920001)
ON CONFLICT (plugin_install_id) DO UPDATE SET
    config_json = EXCLUDED.config_json,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP(3);
