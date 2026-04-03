package io.github.claudetoolkit.ui.dbprofile;

import io.github.claudetoolkit.ui.config.SettingsPersistenceService;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DbProfileService {

    private final DbProfileRepository      repository;
    private final ToolkitSettings          settings;
    private final SettingsPersistenceService persistenceService;

    public DbProfileService(DbProfileRepository repository,
                            ToolkitSettings settings,
                            SettingsPersistenceService persistenceService) {
        this.repository         = repository;
        this.settings           = settings;
        this.persistenceService = persistenceService;
    }

    public DbProfile save(String name, String url, String username, String password, String description) {
        DbProfile p = new DbProfile(name, url, username, password, description);
        return repository.save(p);
    }

    public DbProfile update(Long id, String name, String url, String username, String password, String description) {
        DbProfile p = repository.findById(id).orElse(null);
        if (p == null) return null;
        p.setName(name);
        p.setUrl(url);
        p.setUsername(username);
        if (password != null && !password.isEmpty()) p.setPassword(password);
        p.setDescription(description);
        return repository.save(p);
    }

    @Transactional(readOnly = true)
    public List<DbProfile> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public DbProfile findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /** Apply this profile's DB connection to the active settings and persist. */
    public void applyProfile(Long id) {
        DbProfile p = repository.findById(id).orElse(null);
        if (p == null) return;
        settings.getDb().setUrl(p.getUrl());
        settings.getDb().setUsername(p.getUsername());
        settings.getDb().setPassword(p.getPassword());
        persistenceService.save();
    }
}
