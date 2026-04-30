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

    /**
     * v4.7.x — #G3 Live DB Phase 0: Live DB 분석 가능 여부 토글.
     *
     * <p>활성화는 사용자 책임 — 이 프로필의 user 가 *읽기 전용* 권한만 가져야 함.
     * 코드 레벨 게이트({@link io.github.claudetoolkit.ui.livedb.SqlClassifier})
     * 와 DB 권한 *이중 차단* 으로 안전성 확보.
     *
     * @return 토글 후 상태 (활성/비활성)
     */
    public boolean toggleLiveAnalysis(Long id, boolean enabled) {
        DbProfile p = repository.findById(id).orElse(null);
        if (p == null) return false;
        p.setReadOnlyForLiveAnalysis(enabled);
        repository.save(p);
        return enabled;
    }

    /**
     * v4.7.x — Live DB 분석에 사용 가능한 활성 프로필만 반환 (UI dropdown 용).
     */
    @Transactional(readOnly = true)
    public List<DbProfile> findActiveLiveAnalysisProfiles() {
        List<DbProfile> all = findAll();
        java.util.List<DbProfile> filtered = new java.util.ArrayList<DbProfile>();
        for (DbProfile p : all) {
            if (p.isReadOnlyForLiveAnalysis()) filtered.add(p);
        }
        return filtered;
    }
}
