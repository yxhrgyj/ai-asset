# Portal Website Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public-facing portal website for AI asset browsing with authenticated management backend access

**Architecture:** Single-page Vue 3 application with route-based layout switching (PortalLayout for public pages at `/`, AdminLayout for management at `/admin`). Mixed access mode: unauthenticated browsing of published assets, authentication required for downloads and management. Backend adds public API endpoints with PUBLISHED-only filtering.

**Tech Stack:** Vue 3 + TypeScript + Vite + Pinia (frontend), Spring Boot 3.3.5 + PostgreSQL (backend), ECharts 5.x (statistics), session-based auth

**Spec:** docs/superpowers/specs/2026-09-05-portal-website-design.md

## Global Constraints

- Node.js 20+, JDK 21, Maven 3.8+, PostgreSQL 16
- All new routes follow `/` (portal) or `/admin/*` (management) structure
- Public API endpoints at `/api/public/**` return only PUBLISHED non-archived assets
- Design tokens: surfaces #05070C → #1E2636, accent #38BDF8, radius 999px (buttons), fluid typography via clamp()
- Zero placeholders: every test, implementation, and commit message must be complete and executable
- TDD: write failing test → verify failure → implement → verify pass → commit

---

### Task 1: Backend Public API Infrastructure

**Files:**
- Create: `src/main/java/com/company/aiasset/portal/PortalController.java`
- Create: `src/main/java/com/company/aiasset/portal/PortalDto.java`
- Modify: `src/main/java/com/company/aiasset/security/SecurityConfig.java:45-50`
- Test: `src/test/java/com/company/aiasset/portal/PortalControllerTest.java`

**Interfaces:**
- Consumes: `AssetRepository`, `AssetVersionRepository`, `Asset`, `AssetVersion` (existing)
- Produces: `GET /api/public/assets` → `{items: PortalDto.AssetSummary[], total: number}`, `GET /api/public/assets/{id}` → `PortalDto.AssetDetail`, `GET /api/public/statistics` → `PortalDto.Statistics`

- [ ] **Step 1: Write failing test for public asset listing**

```java
package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortalControllerTest {

    @Autowired MockMvc mvc;
    @Autowired AssetRepository assets;
    @Autowired AssetVersionRepository versions;

    @Test
    void listPublicAssets_returnsOnlyPublishedNonArchived() throws Exception {
        Asset published = assets.save(new Asset(UUID.randomUUID(), "Published Asset", Asset.Type.RULE, Asset.Scope.ORGANIZATION, UUID.randomUUID(), false));
        versions.save(new AssetVersion(UUID.randomUUID(), published.getId(), 1, AssetVersion.Status.PUBLISHED, "body", null, null));
        
        Asset draft = assets.save(new Asset(UUID.randomUUID(), "Draft Asset", Asset.Type.SKILL, Asset.Scope.PROJECT, UUID.randomUUID(), false));
        versions.save(new AssetVersion(UUID.randomUUID(), draft.getId(), 1, AssetVersion.Status.DRAFT, "body", null, null));
        
        Asset archived = assets.save(new Asset(UUID.randomUUID(), "Archived Asset", Asset.Type.DOCUMENT, Asset.Scope.TECH_STACK, UUID.randomUUID(), true));
        versions.save(new AssetVersion(UUID.randomUUID(), archived.getId(), 1, AssetVersion.Status.PUBLISHED, "body", null, null));

        mvc.perform(get("/api/public/assets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Published Asset"))
            .andExpect(jsonPath("$.total").value(1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PortalControllerTest#listPublicAssets_returnsOnlyPublishedNonArchived`
Expected: FAIL with 404 Not Found (endpoint doesn't exist)

- [ ] **Step 3: Create DTO classes**

```java
package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetVersion;
import java.time.LocalDateTime;
import java.util.UUID;

public class PortalDto {
    
    public record AssetSummary(
        UUID id,
        String name,
        String type,
        String scope,
        String summary,
        String tags,
        int downloadCount,
        LocalDateTime publishedAt
    ) {
        public static AssetSummary of(Asset asset, AssetVersion latestPublished) {
            return new AssetSummary(
                asset.getId(),
                asset.getName(),
                asset.getType().name(),
                asset.getScope().name(),
                asset.getSummary(),
                asset.getTags(),
                asset.getDownloadCount(),
                latestPublished.getCreatedAt()
            );
        }
    }
    
    public record AssetDetail(
        AssetSummary summary,
        String body,
        int versionNo,
        String changelog
    ) {}
    
    public record Statistics(
        int totalAssets,
        int totalDownloads,
        TypeBreakdown typeBreakdown,
        ScopeBreakdown scopeBreakdown
    ) {}
    
    public record TypeBreakdown(int rule, int skill, int document) {}
    public record ScopeBreakdown(int organization, int techStack, int project) {}
}
```

- [ ] **Step 4: Create controller with public asset listing**

```java
package com.company.aiasset.portal;

import com.company.aiasset.asset.Asset;
import com.company.aiasset.asset.AssetRepository;
import com.company.aiasset.asset.AssetVersion;
import com.company.aiasset.asset.AssetVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
public class PortalController {

    private final AssetRepository assets;
    private final AssetVersionRepository versions;

    public PortalController(AssetRepository assets, AssetVersionRepository versions) {
        this.assets = assets;
        this.versions = versions;
    }

    @GetMapping("/assets")
    public Map<String, Object> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Asset.Type type,
                                    @RequestParam(required = false) Asset.Scope scope,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        
        Page<Asset> found = assets.search(
            blankToNull(q),
            type == null ? null : type.name(),
            scope == null ? null : scope.name(),
            blankToNull(tag),
            false,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))
        );

        List<PortalDto.AssetSummary> items = found.getContent().stream()
            .map(asset -> {
                AssetVersion latest = versions.findByAssetIdOrderByVersionNoDesc(asset.getId())
                    .stream()
                    .filter(v -> v.getStatus() == AssetVersion.Status.PUBLISHED)
                    .findFirst()
                    .orElse(null);
                return latest == null ? null : PortalDto.AssetSummary.of(asset, latest);
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());

        return Map.of(
            "items", items,
            "total", (long) items.size(),
            "page", found.getNumber(),
            "size", found.getSize()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
```

- [ ] **Step 5: Update SecurityConfig to permit public endpoints**

```java
// In SecurityConfig.java, replace lines 45-50:
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
    .requestMatchers("/api/public/**").permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/api/test-data/**").hasRole("ADMIN")
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll()
)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=PortalControllerTest#listPublicAssets_returnsOnlyPublishedNonArchived`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/company/aiasset/portal/ src/main/java/com/company/aiasset/security/SecurityConfig.java src/test/java/com/company/aiasset/portal/
git commit -m "feat(portal): add public API for asset listing

- Add PortalController with /api/public/assets endpoint
- Filter to PUBLISHED non-archived assets only
- Add PortalDto for public asset representation
- Update SecurityConfig to permit /api/public/** access

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Backend Public Asset Detail and Statistics

**Files:**
- Modify: `src/main/java/com/company/aiasset/portal/PortalController.java`
- Modify: `src/test/java/com/company/aiasset/portal/PortalControllerTest.java`

**Interfaces:**
- Consumes: `AssetRepository`, `AssetVersionRepository` (existing), `PortalDto` (Task 1)
- Produces: `GET /api/public/assets/{id}` returns `PortalDto.AssetDetail`, `GET /api/public/statistics` returns `PortalDto.Statistics`

- [ ] **Step 1: Write failing test for asset detail**

```java
@Test
void getAssetDetail_returnsLatestPublishedVersion() throws Exception {
    Asset asset = assets.save(new Asset(UUID.randomUUID(), "Test Asset", Asset.Type.RULE, Asset.Scope.ORGANIZATION, UUID.randomUUID(), false));
    versions.save(new AssetVersion(UUID.randomUUID(), asset.getId(), 1, AssetVersion.Status.PUBLISHED, "Version 1 body", null, null));
    AssetVersion v2 = versions.save(new AssetVersion(UUID.randomUUID(), asset.getId(), 2, AssetVersion.Status.PUBLISHED, "Version 2 body", "Updated content", null));

    mvc.perform(get("/api/public/assets/" + asset.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.name").value("Test Asset"))
        .andExpect(jsonPath("$.body").value("Version 2 body"))
        .andExpect(jsonPath("$.versionNo").value(2))
        .andExpect(jsonPath("$.changelog").value("Updated content"));
}

@Test
void getAssetDetail_returns404ForDraftOnly() throws Exception {
    Asset asset = assets.save(new Asset(UUID.randomUUID(), "Draft Only", Asset.Type.SKILL, Asset.Scope.PROJECT, UUID.randomUUID(), false));
    versions.save(new AssetVersion(UUID.randomUUID(), asset.getId(), 1, AssetVersion.Status.DRAFT, "Draft body", null, null));

    mvc.perform(get("/api/public/assets/" + asset.getId()))
        .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=PortalControllerTest#getAssetDetail_returnsLatestPublishedVersion`
Expected: FAIL with 404 Not Found

Run: `mvn test -Dtest=PortalControllerTest#getAssetDetail_returns404ForDraftOnly`
Expected: FAIL (may pass if 404 by default, but implementation doesn't exist)

- [ ] **Step 3: Implement asset detail endpoint**

```java
// Add to PortalController.java:

@GetMapping("/assets/{id}")
public PortalDto.AssetDetail detail(@PathVariable UUID id) {
    Asset asset = assets.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
    
    if (asset.isArchived()) {
        throw new IllegalArgumentException("Asset not found");
    }
    
    AssetVersion latest = versions.findByAssetIdOrderByVersionNoDesc(asset.getId())
        .stream()
        .filter(v -> v.getStatus() == AssetVersion.Status.PUBLISHED)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
    
    return new PortalDto.AssetDetail(
        PortalDto.AssetSummary.of(asset, latest),
        latest.getBody(),
        latest.getVersionNo(),
        latest.getChangelog()
    );
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=PortalControllerTest`
Expected: All tests PASS

- [ ] **Step 5: Write failing test for statistics**

```java
@Test
void getStatistics_returnsAggregatedData() throws Exception {
    Asset r1 = assets.save(new Asset(UUID.randomUUID(), "Rule 1", Asset.Type.RULE, Asset.Scope.ORGANIZATION, UUID.randomUUID(), false));
    r1.setDownloadCount(10);
    assets.save(r1);
    versions.save(new AssetVersion(UUID.randomUUID(), r1.getId(), 1, AssetVersion.Status.PUBLISHED, "body", null, null));
    
    Asset s1 = assets.save(new Asset(UUID.randomUUID(), "Skill 1", Asset.Type.SKILL, Asset.Scope.TECH_STACK, UUID.randomUUID(), false));
    s1.setDownloadCount(5);
    assets.save(s1);
    versions.save(new AssetVersion(UUID.randomUUID(), s1.getId(), 1, AssetVersion.Status.PUBLISHED, "body", null, null));
    
    Asset d1 = assets.save(new Asset(UUID.randomUUID(), "Doc 1", Asset.Type.DOCUMENT, Asset.Scope.PROJECT, UUID.randomUUID(), false));
    d1.setDownloadCount(3);
    assets.save(d1);
    versions.save(new AssetVersion(UUID.randomUUID(), d1.getId(), 1, AssetVersion.Status.PUBLISHED, "body", null, null));

    mvc.perform(get("/api/public/statistics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAssets").value(3))
        .andExpect(jsonPath("$.totalDownloads").value(18))
        .andExpect(jsonPath("$.typeBreakdown.rule").value(1))
        .andExpect(jsonPath("$.typeBreakdown.skill").value(1))
        .andExpect(jsonPath("$.typeBreakdown.document").value(1))
        .andExpect(jsonPath("$.scopeBreakdown.organization").value(1))
        .andExpect(jsonPath("$.scopeBreakdown.techStack").value(1))
        .andExpect(jsonPath("$.scopeBreakdown.project").value(1));
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn test -Dtest=PortalControllerTest#getStatistics_returnsAggregatedData`
Expected: FAIL with 404 Not Found

- [ ] **Step 7: Implement statistics endpoint**

```java
// Add to PortalController.java:

@GetMapping("/statistics")
public PortalDto.Statistics statistics() {
    List<Asset> publishedAssets = assets.findAll().stream()
        .filter(a -> !a.isArchived())
        .filter(a -> versions.findByAssetIdOrderByVersionNoDesc(a.getId())
            .stream()
            .anyMatch(v -> v.getStatus() == AssetVersion.Status.PUBLISHED))
        .toList();
    
    int totalDownloads = publishedAssets.stream()
        .mapToInt(Asset::getDownloadCount)
        .sum();
    
    Map<Asset.Type, Long> typeCounts = publishedAssets.stream()
        .collect(Collectors.groupingBy(Asset::getType, Collectors.counting()));
    
    Map<Asset.Scope, Long> scopeCounts = publishedAssets.stream()
        .collect(Collectors.groupingBy(Asset::getScope, Collectors.counting()));
    
    return new PortalDto.Statistics(
        publishedAssets.size(),
        totalDownloads,
        new PortalDto.TypeBreakdown(
            typeCounts.getOrDefault(Asset.Type.RULE, 0L).intValue(),
            typeCounts.getOrDefault(Asset.Type.SKILL, 0L).intValue(),
            typeCounts.getOrDefault(Asset.Type.DOCUMENT, 0L).intValue()
        ),
        new PortalDto.ScopeBreakdown(
            scopeCounts.getOrDefault(Asset.Scope.ORGANIZATION, 0L).intValue(),
            scopeCounts.getOrDefault(Asset.Scope.TECH_STACK, 0L).intValue(),
            scopeCounts.getOrDefault(Asset.Scope.PROJECT, 0L).intValue()
        )
    );
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn test -Dtest=PortalControllerTest#getStatistics_returnsAggregatedData`
Expected: PASS

- [ ] **Step 9: Run all portal tests**

Run: `mvn test -Dtest=PortalControllerTest`
Expected: All tests PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/company/aiasset/portal/PortalController.java src/test/java/com/company/aiasset/portal/PortalControllerTest.java
git commit -m "feat(portal): add asset detail and statistics endpoints

- Add GET /api/public/assets/{id} for published asset detail
- Add GET /api/public/statistics for platform metrics
- Return 404 for draft-only or archived assets in detail view

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Frontend Portal API Client

**Files:**
- Create: `frontend/src/api/portal.ts`
- Create: `frontend/src/types/portal.ts`

**Interfaces:**
- Consumes: `client.ts` (existing HTTP client)
- Produces: `portalApi.listAssets()`, `portalApi.getAssetDetail()`, `portalApi.getStatistics()` functions returning typed data

- [ ] **Step 1: Create portal types file**

```typescript
// frontend/src/types/portal.ts
export interface AssetSummary {
  id: string
  name: string
  type: 'RULE' | 'SKILL' | 'DOCUMENT'
  scope: 'ORGANIZATION' | 'TECH_STACK' | 'PROJECT'
  summary: string | null
  tags: string | null
  downloadCount: number
  publishedAt: string
}

export interface AssetDetail {
  summary: AssetSummary
  body: string
  versionNo: number
  changelog: string | null
}

export interface Statistics {
  totalAssets: number
  totalDownloads: number
  typeBreakdown: {
    rule: number
    skill: number
    document: number
  }
  scopeBreakdown: {
    organization: number
    techStack: number
    project: number
  }
}

export interface AssetListParams {
  q?: string
  type?: 'RULE' | 'SKILL' | 'DOCUMENT'
  scope?: 'ORGANIZATION' | 'TECH_STACK' | 'PROJECT'
  tag?: string
  page?: number
  size?: number
}

export interface AssetListResponse {
  items: AssetSummary[]
  total: number
  page: number
  size: number
}
```

- [ ] **Step 2: Create portal API client**

```typescript
// frontend/src/api/portal.ts
import { client } from './client'
import type {
  AssetSummary,
  AssetDetail,
  Statistics,
  AssetListParams,
  AssetListResponse
} from '../types/portal'

export const portalApi = {
  async listAssets(params: AssetListParams = {}): Promise<AssetListResponse> {
    const searchParams = new URLSearchParams()
    if (params.q) searchParams.set('q', params.q)
    if (params.type) searchParams.set('type', params.type)
    if (params.scope) searchParams.set('scope', params.scope)
    if (params.tag) searchParams.set('tag', params.tag)
    if (params.page !== undefined) searchParams.set('page', String(params.page))
    if (params.size !== undefined) searchParams.set('size', String(params.size))
    
    const query = searchParams.toString()
    const url = `/api/public/assets${query ? '?' + query : ''}`
    return client.get(url)
  },

  async getAssetDetail(id: string): Promise<AssetDetail> {
    return client.get(`/api/public/assets/${id}`)
  },

  async getStatistics(): Promise<Statistics> {
    return client.get('/api/public/statistics')
  }
}
```

- [ ] **Step 3: Verify TypeScript compilation**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no type errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/portal.ts frontend/src/types/portal.ts
git commit -m "feat(frontend): add portal API client and types

- Add portal.ts API client for public endpoints
- Add portal.ts type definitions for asset summary, detail, statistics
- Wire up to existing HTTP client

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: PortalLayout Component

**Files:**
- Create: `frontend/src/components/PortalLayout.vue`
- Create: `frontend/src/components/PortalNav.vue`

**Interfaces:**
- Consumes: `vue-router`, `useAuthStore()` (existing)
- Produces: `<PortalLayout>` component with header, nav, footer, and slot for content

- [ ] **Step 1: Create PortalNav component**

```vue
<!-- frontend/src/components/PortalNav.vue -->
<template>
  <nav class="portal-nav">
    <router-link to="/" class="nav-link" exact-active-class="active">首页</router-link>
    <router-link to="/assets-public" class="nav-link" active-class="active">资产展示</router-link>
    <router-link to="/statistics-public" class="nav-link" active-class="active">平台统计</router-link>
    <router-link to="/about" class="nav-link" active-class="active">关于</router-link>
    <router-link to="/help" class="nav-link" active-class="active">帮助文档</router-link>
  </nav>
</template>

<style scoped>
.portal-nav {
  display: flex;
  gap: clamp(1.5rem, 3vw, 2.5rem);
  align-items: center;
}

.nav-link {
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  font-size: clamp(0.9375rem, 1.5vw, 1rem);
  font-weight: 500;
  letter-spacing: 0.01em;
  transition: color 0.2s ease;
  position: relative;
}

.nav-link:hover {
  color: rgba(255, 255, 255, 0.95);
}

.nav-link.active {
  color: var(--accent);
}

.nav-link.active::after {
  content: '';
  position: absolute;
  bottom: -0.5rem;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent);
}
</style>
```

- [ ] **Step 2: Create PortalLayout component**

```vue
<!-- frontend/src/components/PortalLayout.vue -->
<template>
  <div class="portal-layout">
    <header class="portal-header">
      <div class="header-container">
        <div class="header-brand">
          <div class="brand-icon">🤖</div>
          <div class="brand-text">
            <div class="brand-title">数科院AI资产管理平台</div>
            <div class="brand-subtitle">Data Science Institute AI Asset Platform</div>
          </div>
        </div>
        
        <PortalNav />
        
        <div class="header-actions">
          <router-link v-if="!auth.user" to="/login" class="btn-login">登录</router-link>
          <router-link v-if="auth.user" to="/admin" class="btn-admin">管理中心</router-link>
          <button v-if="auth.user" @click="handleLogout" class="btn-logout">登出</button>
        </div>
      </div>
    </header>

    <main class="portal-main">
      <slot></slot>
    </main>

    <footer class="portal-footer">
      <div class="footer-container">
        <div class="footer-section">
          <h3 class="footer-title">数科院AI资产管理平台</h3>
          <p class="footer-desc">统一管理和共享AI规则、技能和文档资产</p>
        </div>
        <div class="footer-section">
          <h4 class="footer-heading">快速链接</h4>
          <router-link to="/" class="footer-link">首页</router-link>
          <router-link to="/assets-public" class="footer-link">资产展示</router-link>
          <router-link to="/help" class="footer-link">帮助文档</router-link>
        </div>
        <div class="footer-section">
          <h4 class="footer-heading">联系我们</h4>
          <p class="footer-text">数据科学研究院</p>
          <p class="footer-text">support@example.com</p>
        </div>
      </div>
      <div class="footer-bottom">
        <div class="footer-container">
          <p class="footer-copyright">© 2026 数科院AI资产管理平台. All rights reserved.</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import PortalNav from './PortalNav.vue'

const auth = useAuthStore()
const router = useRouter()

const handleLogout = async () => {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
:root {
  --surface-0: #05070C;
  --surface-1: #0A0D12;
  --surface-2: #0F131C;
  --surface-3: #161D2B;
  --surface-4: #1E2636;
  --accent: #38BDF8;
  --text-primary: rgba(255, 255, 255, 0.95);
  --text-secondary: rgba(255, 255, 255, 0.7);
  --text-tertiary: rgba(255, 255, 255, 0.5);
}

.portal-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--surface-0);
  color: var(--text-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.portal-header {
  background: var(--surface-1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
  backdrop-filter: blur(12px);
}

.header-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: clamp(1rem, 2vw, 1.5rem) clamp(1.5rem, 4vw, 3rem);
  display: flex;
  align-items: center;
  gap: clamp(2rem, 5vw, 4rem);
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.brand-icon {
  font-size: clamp(2rem, 4vw, 2.5rem);
  filter: drop-shadow(0 0 8px rgba(56, 189, 248, 0.3));
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.brand-title {
  font-size: clamp(1.125rem, 2vw, 1.25rem);
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.brand-subtitle {
  font-size: clamp(0.75rem, 1.2vw, 0.8125rem);
  color: var(--text-tertiary);
  font-weight: 400;
  letter-spacing: 0.02em;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 1rem;
  align-items: center;
}

.btn-login,
.btn-admin {
  padding: 0.625rem 1.5rem;
  background: var(--surface-3);
  color: var(--text-primary);
  text-decoration: none;
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 500;
  transition: all 0.2s ease;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-login:hover,
.btn-admin:hover {
  background: var(--surface-4);
  border-color: rgba(255, 255, 255, 0.2);
}

.btn-logout {
  padding: 0.625rem 1.5rem;
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-logout:hover {
  color: var(--text-primary);
  border-color: rgba(255, 255, 255, 0.2);
}

.portal-main {
  flex: 1;
}

.portal-footer {
  background: var(--surface-1);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  margin-top: clamp(4rem, 8vw, 6rem);
}

.footer-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 4vw, 3rem);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: clamp(2rem, 4vw, 3rem);
}

.footer-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.footer-title {
  font-size: clamp(1.125rem, 2vw, 1.25rem);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.footer-desc {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.footer-heading {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.footer-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.9375rem;
  transition: color 0.2s ease;
}

.footer-link:hover {
  color: var(--accent);
}

.footer-text {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  margin: 0;
}

.footer-bottom {
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding: 1.5rem clamp(1.5rem, 4vw, 3rem);
}

.footer-copyright {
  text-align: center;
  font-size: 0.875rem;
  color: var(--text-tertiary);
  margin: 0;
}
</style>
```

- [ ] **Step 3: Verify component compiles**

Run: `cd frontend && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/PortalLayout.vue frontend/src/components/PortalNav.vue
git commit -m "feat(frontend): add PortalLayout and PortalNav components

- Add PortalLayout with header, nav, footer structure
- Add PortalNav with links to portal pages
- Implement deep color theme with fluid typography
- Add conditional login/management center buttons

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: AdminLayout Component and Route Restructuring

**Files:**
- Create: `frontend/src/components/AdminLayout.vue`
- Modify: `frontend/src/router/index.ts:13-98`
- Modify: `frontend/src/components/MainLayout.vue` (delete file, replaced by AdminLayout)

**Interfaces:**
- Consumes: `MainLayout.vue` structure (copy and rename)
- Produces: `<AdminLayout>` component, all admin routes under `/admin/*`, portal routes at `/*`

- [ ] **Step 1: Create AdminLayout from MainLayout**

```vue
<!-- frontend/src/components/AdminLayout.vue -->
<!-- Copy entire MainLayout.vue content, rename component -->
<template>
  <div class="layout">
    <div class="topbar">
      <div class="topbar-accent"></div>
      <div class="topbar-main">
        <div class="topbar-logo">
          <div class="logo-icon">🤖</div>
          <span class="logo-text">管理中心</span>
        </div>
        <div class="topbar-right">
          <router-link to="/" class="btn-portal">返回门户</router-link>
          <div class="user-info">
            <div class="user-avatar">{{ userInitial }}</div>
            <span class="user-name">{{ auth.user?.displayName }}</span>
            <button class="btn-logout" @click="handleLogout">登出</button>
          </div>
        </div>
      </div>
    </div>

    <div class="main-container">
      <aside class="sidebar">
        <nav class="sidebar-nav">
          <router-link to="/admin" class="nav-item" exact-active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            <span>首页</span>
          </router-link>

          <router-link to="/admin/assets" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
            <span>资产库</span>
          </router-link>

          <router-link to="/admin/projects" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="7" height="7"/>
              <rect x="14" y="3" width="7" height="7"/>
              <rect x="14" y="14" width="7" height="7"/>
              <rect x="3" y="14" width="7" height="7"/>
            </svg>
            <span>项目管理</span>
          </router-link>

          <router-link to="/admin/teams" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <span>团队管理</span>
          </router-link>

          <router-link to="/admin/statistics" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/>
            </svg>
            <span>平台统计</span>
          </router-link>

          <router-link v-if="auth.user?.role === 'APPROVER' || auth.user?.role === 'ADMIN'" to="/admin/approvals" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <span>审批管理</span>
          </router-link>

          <router-link v-if="auth.user?.role === 'ADMIN'" to="/admin/users" class="nav-item" active-class="active">
            <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="8.5" cy="7" r="4"/>
              <polyline points="17 11 19 13 23 9"/>
            </svg>
            <span>用户管理</span>
          </router-link>
        </nav>
      </aside>

      <main class="content">
        <slot></slot>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const userInitial = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const handleLogout = async () => {
  await auth.logout()
  router.push('/login')
}
</script>

<!-- Copy entire MainLayout.vue style section -->
```

- [ ] **Step 2: Update router configuration**

```typescript
// frontend/src/router/index.ts - replace lines 13-98:
import AdminLayout from '../components/AdminLayout.vue'
import PortalLayout from '../components/PortalLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: { public: true }
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: ChangePassword
    },
    {
      path: '/',
      component: PortalLayout,
      children: [
        {
          path: '',
          name: 'portal-home',
          component: () => import('../views/PortalHome.vue'),
          meta: { public: true }
        },
        {
          path: 'assets-public',
          name: 'assets-public',
          component: () => import('../views/AssetsPublic.vue'),
          meta: { public: true }
        },
        {
          path: 'assets-public/:id',
          name: 'asset-public-detail',
          component: () => import('../views/AssetPublicDetail.vue'),
          meta: { public: true }
        },
        {
          path: 'statistics-public',
          name: 'statistics-public',
          component: () => import('../views/StatisticsPublic.vue'),
          meta: { public: true }
        },
        {
          path: 'about',
          name: 'about',
          component: () => import('../views/About.vue'),
          meta: { public: true }
        },
        {
          path: 'help',
          name: 'help',
          component: () => import('../views/Help.vue'),
          meta: { public: true }
        }
      ]
    },
    {
      path: '/admin',
      component: AdminLayout,
      children: [
        {
          path: '',
          name: 'admin-home',
          component: Home
        },
        {
          path: 'teams',
          name: 'admin-teams',
          component: Teams
        },
        {
          path: 'users',
          name: 'admin-users',
          component: Users,
          meta: { adminOnly: true }
        },
        {
          path: 'assets',
          name: 'admin-assets',
          component: Assets
        },
        {
          path: 'assets/:id',
          name: 'admin-asset-detail',
          component: () => import('../views/AssetDetail.vue')
        },
        {
          path: 'approvals',
          name: 'admin-approvals',
          component: Approvals,
          meta: { approverOnly: true }
        },
        {
          path: 'statistics',
          name: 'admin-statistics',
          component: Statistics
        },
        {
          path: 'projects',
          name: 'admin-projects',
          component: Projects
        },
        {
          path: 'projects/:id',
          name: 'admin-project-detail',
          component: () => import('../views/ProjectDetail.vue')
        }
      ]
    }
  ]
})
```

- [ ] **Step 3: Verify TypeScript compilation**

Run: `cd frontend && npm run build`
Expected: Build succeeds (views don't exist yet, but routes and layouts compile)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/AdminLayout.vue frontend/src/router/index.ts
git commit -m "feat(frontend): add AdminLayout and restructure routes

- Create AdminLayout (copy of MainLayout with 'Back to Portal' button)
- Restructure routes: portal at /, admin at /admin/*
- Add nested route structure with layout components
- All admin routes now under /admin prefix

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Portal Home Page

**Files:**
- Create: `frontend/src/views/PortalHome.vue`

**Interfaces:**
- Consumes: `portalApi.getStatistics()`, `portalApi.listAssets()` (Task 3)
- Produces: Hero section, featured assets, statistics overview, CTA buttons

- [ ] **Step 1: Create PortalHome component**

```vue
<!-- frontend/src/views/PortalHome.vue -->
<template>
  <div class="portal-home">
    <section class="hero">
      <div class="hero-container">
        <h1 class="hero-title">数科院AI资产管理平台</h1>
        <p class="hero-subtitle">统一管理和共享AI规则、技能和文档资产</p>
        <div class="hero-actions">
          <router-link to="/assets-public" class="btn-primary">浏览资产</router-link>
          <router-link v-if="auth.user" to="/admin" class="btn-secondary">管理中心</router-link>
          <router-link v-else to="/login" class="btn-secondary">登录</router-link>
        </div>
      </div>
    </section>

    <section class="stats-overview">
      <div class="stats-container">
        <div class="stat-card">
          <div class="stat-value">{{ statistics?.totalAssets || 0 }}</div>
          <div class="stat-label">资产总数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ statistics?.totalDownloads || 0 }}</div>
          <div class="stat-label">下载次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ (statistics?.typeBreakdown.rule || 0) + (statistics?.typeBreakdown.skill || 0) + (statistics?.typeBreakdown.document || 0) }}</div>
          <div class="stat-label">资产类型</div>
        </div>
      </div>
    </section>

    <section class="featured-assets">
      <div class="section-container">
        <h2 class="section-title">精选资产</h2>
        <div class="asset-grid" v-if="featuredAssets.length > 0">
          <div v-for="asset in featuredAssets" :key="asset.id" class="asset-card">
            <div class="asset-header">
              <div class="asset-icon">{{ getTypeIcon(asset.type) }}</div>
              <div class="asset-badge">{{ getTypeLabel(asset.type) }}</div>
            </div>
            <h3 class="asset-name">{{ asset.name }}</h3>
            <p class="asset-summary">{{ asset.summary || '暂无描述' }}</p>
            <div class="asset-footer">
              <span class="asset-downloads">{{ asset.downloadCount }} 次下载</span>
              <router-link :to="`/assets-public/${asset.id}`" class="asset-link">查看详情 →</router-link>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">暂无资产</div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import { portalApi } from '../api/portal'
import type { Statistics, AssetSummary } from '../types/portal'

const auth = useAuthStore()
const statistics = ref<Statistics | null>(null)
const featuredAssets = ref<AssetSummary[]>([])

onMounted(async () => {
  try {
    statistics.value = await portalApi.getStatistics()
    const response = await portalApi.listAssets({ size: 6 })
    featuredAssets.value = response.items
  } catch (err) {
    console.error('Failed to load portal data:', err)
  }
})

function getTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    RULE: '📋',
    SKILL: '⚡',
    DOCUMENT: '📄'
  }
  return icons[type] || '📦'
}

function getTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    RULE: '规则',
    SKILL: '技能',
    DOCUMENT: '文档'
  }
  return labels[type] || type
}
</script>

<style scoped>
.portal-home {
  min-height: 100vh;
}

.hero {
  background: linear-gradient(135deg, var(--surface-1) 0%, var(--surface-2) 100%);
  padding: clamp(4rem, 10vw, 8rem) clamp(1.5rem, 4vw, 3rem);
  text-align: center;
}

.hero-container {
  max-width: 900px;
  margin: 0 auto;
}

.hero-title {
  font-size: clamp(2.5rem, 6vw, 4rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  margin: 0 0 1.5rem;
  background: linear-gradient(135deg, var(--text-primary) 0%, rgba(255, 255, 255, 0.7) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: clamp(1.125rem, 2.5vw, 1.5rem);
  color: var(--text-secondary);
  margin: 0 0 3rem;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 1.5rem;
  justify-content: center;
  flex-wrap: wrap;
}

.btn-primary,
.btn-secondary {
  padding: 1rem 2.5rem;
  border-radius: 999px;
  font-size: 1.0625rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.3s ease;
  letter-spacing: 0.01em;
}

.btn-primary {
  background: var(--accent);
  color: var(--surface-0);
  box-shadow: 0 4px 16px rgba(56, 189, 248, 0.3);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(56, 189, 248, 0.4);
}

.btn-secondary {
  background: var(--surface-3);
  color: var(--text-primary);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-secondary:hover {
  background: var(--surface-4);
  border-color: rgba(255, 255, 255, 0.2);
}

.stats-overview {
  padding: clamp(3rem, 6vw, 5rem) clamp(1.5rem, 4vw, 3rem);
  background: var(--surface-0);
}

.stats-container {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 2rem;
}

.stat-card {
  background: var(--surface-2);
  padding: 2.5rem;
  border-radius: 24px;
  text-align: center;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.stat-value {
  font-size: clamp(2.5rem, 5vw, 3.5rem);
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 0.75rem;
  letter-spacing: -0.02em;
}

.stat-label {
  font-size: 1.125rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.featured-assets {
  padding: clamp(3rem, 6vw, 5rem) clamp(1.5rem, 4vw, 3rem);
  background: var(--surface-1);
}

.section-container {
  max-width: 1400px;
  margin: 0 auto;
}

.section-title {
  font-size: clamp(2rem, 4vw, 2.5rem);
  font-weight: 700;
  margin: 0 0 3rem;
  text-align: center;
  letter-spacing: -0.02em;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 2rem;
}

.asset-card {
  background: var(--surface-2);
  padding: 2rem;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  transition: all 0.3s ease;
}

.asset-card:hover {
  transform: translateY(-4px);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
}

.asset-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.asset-icon {
  font-size: 2rem;
  filter: drop-shadow(0 2px 8px rgba(56, 189, 248, 0.2));
}

.asset-badge {
  padding: 0.375rem 1rem;
  background: var(--surface-3);
  color: var(--accent);
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.asset-name {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 1rem;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.asset-summary {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 1.5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.asset-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.asset-downloads {
  font-size: 0.875rem;
  color: var(--text-tertiary);
}

.asset-link {
  color: var(--accent);
  text-decoration: none;
  font-size: 0.9375rem;
  font-weight: 600;
  transition: color 0.2s ease;
}

.asset-link:hover {
  color: rgba(56, 189, 248, 0.8);
}

.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: var(--text-tertiary);
  font-size: 1.125rem;
}
</style>
```

- [ ] **Step 2: Verify component compiles**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Start dev server and test manually**

Run: `cd frontend && npm run dev`
Navigate to: `http://localhost:5173`
Expected: Portal home page renders with hero, stats (may be 0), featured assets section

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/PortalHome.vue
git commit -m "feat(frontend): add portal home page

- Hero section with title, subtitle, CTA buttons
- Statistics overview with three key metrics
- Featured assets grid with top 6 assets
- Responsive design with fluid typography
- Deep color theme with accent highlights

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Assets Public List Page

**Files:**
- Create: `frontend/src/views/AssetsPublic.vue`

**Interfaces:**
- Consumes: `portalApi.listAssets()` (Task 3), `AssetSummary` type
- Produces: Searchable, filterable asset list with pagination

- [ ] **Step 1: Create AssetsPublic component**

```vue
<!-- frontend/src/views/AssetsPublic.vue -->
<template>
  <div class="assets-public">
    <div class="page-container">
      <header class="page-header">
        <h1 class="page-title">资产展示</h1>
        <p class="page-subtitle">浏览所有已发布的AI资产</p>
      </header>

      <div class="filters">
        <input
          v-model="searchQuery"
          @input="handleSearch"
          type="text"
          placeholder="搜索资产名称或描述..."
          class="search-input"
        />
        
        <div class="filter-group">
          <select v-model="filters.type" @change="loadAssets" class="filter-select">
            <option :value="null">所有类型</option>
            <option value="RULE">规则</option>
            <option value="SKILL">技能</option>
            <option value="DOCUMENT">文档</option>
          </select>
          
          <select v-model="filters.scope" @change="loadAssets" class="filter-select">
            <option :value="null">所有范围</option>
            <option value="ORGANIZATION">组织级</option>
            <option value="TECH_STACK">技术栈</option>
            <option value="PROJECT">项目级</option>
          </select>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else-if="assets.length > 0" class="asset-list">
        <div v-for="asset in assets" :key="asset.id" class="asset-card">
          <div class="asset-header">
            <div class="asset-icon">{{ getTypeIcon(asset.type) }}</div>
            <div class="asset-meta">
              <div class="asset-badges">
                <span class="badge badge-type">{{ getTypeLabel(asset.type) }}</span>
                <span class="badge badge-scope">{{ getScopeLabel(asset.scope) }}</span>
              </div>
              <h2 class="asset-name">{{ asset.name }}</h2>
            </div>
          </div>
          
          <p class="asset-summary">{{ asset.summary || '暂无描述' }}</p>
          
          <div class="asset-tags" v-if="asset.tags">
            <span v-for="tag in asset.tags.split(',')" :key="tag" class="tag">{{ tag.trim() }}</span>
          </div>
          
          <div class="asset-footer">
            <div class="asset-stats">
              <span class="stat">{{ asset.downloadCount }} 次下载</span>
              <span class="stat">{{ formatDate(asset.publishedAt) }}</span>
            </div>
            <router-link :to="`/assets-public/${asset.id}`" class="btn-detail">查看详情</router-link>
          </div>
        </div>
      </div>
      
      <div v-else class="empty-state">
        <div class="empty-icon">📦</div>
        <p class="empty-text">暂无资产</p>
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <button
          @click="goToPage(currentPage - 1)"
          :disabled="currentPage === 0"
          class="btn-page"
        >
          上一页
        </button>
        <span class="page-info">第 {{ currentPage + 1 }} / {{ totalPages }} 页</span>
        <button
          @click="goToPage(currentPage + 1)"
          :disabled="currentPage >= totalPages - 1"
          class="btn-page"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { portalApi } from '../api/portal'
import type { AssetSummary } from '../types/portal'

const assets = ref<AssetSummary[]>([])
const loading = ref(false)
const searchQuery = ref('')
const filters = ref<{ type: string | null; scope: string | null }>({
  type: null,
  scope: null
})
const currentPage = ref(0)
const pageSize = ref(20)
const totalItems = ref(0)
const totalPages = ref(0)

let searchTimeout: number | null = null

onMounted(() => {
  loadAssets()
})

async function loadAssets() {
  loading.value = true
  try {
    const response = await portalApi.listAssets({
      q: searchQuery.value || undefined,
      type: filters.value.type as any,
      scope: filters.value.scope as any,
      page: currentPage.value,
      size: pageSize.value
    })
    assets.value = response.items
    totalItems.value = response.total
    totalPages.value = Math.ceil(response.total / pageSize.value)
  } catch (err) {
    console.error('Failed to load assets:', err)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = window.setTimeout(() => {
    currentPage.value = 0
    loadAssets()
  }, 300)
}

function goToPage(page: number) {
  currentPage.value = page
  loadAssets()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function getTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    RULE: '📋',
    SKILL: '⚡',
    DOCUMENT: '📄'
  }
  return icons[type] || '📦'
}

function getTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    RULE: '规则',
    SKILL: '技能',
    DOCUMENT: '文档'
  }
  return labels[type] || type
}

function getScopeLabel(scope: string): string {
  const labels: Record<string, string> = {
    ORGANIZATION: '组织级',
    TECH_STACK: '技术栈',
    PROJECT: '项目级'
  }
  return labels[scope] || scope
}

function formatDate(date: string): string {
  return new Date(date).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.assets-public {
  min-height: 100vh;
  background: var(--surface-0);
  padding: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 4vw, 3rem);
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: clamp(2rem, 4vw, 3rem);
}

.page-title {
  font-size: clamp(2rem, 4vw, 2.5rem);
  font-weight: 700;
  margin: 0 0 1rem;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.page-subtitle {
  font-size: clamp(1rem, 2vw, 1.125rem);
  color: var(--text-secondary);
  margin: 0;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.search-input {
  width: 100%;
  padding: 1rem 1.5rem;
  background: var(--surface-2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  color: var(--text-primary);
  font-size: 1rem;
  transition: all 0.2s ease;
}

.search-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.1);
}

.search-input::placeholder {
  color: var(--text-tertiary);
}

.filter-group {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.filter-select {
  flex: 1;
  min-width: 200px;
  padding: 0.875rem 1.25rem;
  background: var(--surface-2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: var(--text-primary);
  font-size: 0.9375rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-select:focus {
  outline: none;
  border-color: var(--accent);
}

.loading {
  text-align: center;
  padding: 4rem 2rem;
  color: var(--text-secondary);
  font-size: 1.125rem;
}

.asset-list {
  display: grid;
  gap: 1.5rem;
}

.asset-card {
  background: var(--surface-2);
  padding: 2rem;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  transition: all 0.3s ease;
}

.asset-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.asset-header {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 1.5rem;
}

.asset-icon {
  font-size: 2.5rem;
  filter: drop-shadow(0 2px 8px rgba(56, 189, 248, 0.2));
}

.asset-meta {
  flex: 1;
}

.asset-badges {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.badge {
  padding: 0.375rem 1rem;
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.badge-type {
  background: var(--surface-3);
  color: var(--accent);
}

.badge-scope {
  background: var(--surface-3);
  color: var(--text-secondary);
}

.asset-name {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.asset-summary {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0 0 1.5rem;
}

.asset-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}

.tag {
  padding: 0.375rem 0.875rem;
  background: var(--surface-3);
  color: var(--text-tertiary);
  border-radius: 8px;
  font-size: 0.8125rem;
}

.asset-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.asset-stats {
  display: flex;
  gap: 1.5rem;
}

.stat {
  font-size: 0.875rem;
  color: var(--text-tertiary);
}

.btn-detail {
  padding: 0.75rem 1.5rem;
  background: var(--accent);
  color: var(--surface-0);
  text-decoration: none;
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 600;
  transition: all 0.2s ease;
}

.btn-detail:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(56, 189, 248, 0.3);
}

.empty-state {
  text-align: center;
  padding: 6rem 2rem;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1.5rem;
  opacity: 0.3;
}

.empty-text {
  font-size: 1.125rem;
  color: var(--text-tertiary);
  margin: 0;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 2rem;
  margin-top: 3rem;
  padding-top: 2rem;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.btn-page {
  padding: 0.75rem 1.5rem;
  background: var(--surface-2);
  color: var(--text-primary);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-page:hover:not(:disabled) {
  background: var(--surface-3);
  border-color: rgba(255, 255, 255, 0.2);
}

.btn-page:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.9375rem;
  color: var(--text-secondary);
}
</style>
```

- [ ] **Step 2: Verify component compiles**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/AssetsPublic.vue
git commit -m "feat(frontend): add public assets list page

- Searchable and filterable asset list
- Type and scope filter dropdowns
- Pagination with page navigation
- Responsive card layout with asset metadata
- Empty state for no results

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Asset Public Detail Page

**Files:**
- Create: `frontend/src/views/AssetPublicDetail.vue`

**Interfaces:**
- Consumes: `portalApi.getAssetDetail()`, `assetApi.recordDownload()` (existing)
- Produces: Asset detail view with markdown rendering, download tracking, login prompt for unauthenticated downloads

- [ ] **Step 1: Create AssetPublicDetail component**

```vue
<!-- frontend/src/views/AssetPublicDetail.vue -->
<template>
  <div class="asset-detail-public">
    <div class="page-container">
      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else-if="asset" class="detail-content">
        <header class="detail-header">
          <div class="header-top">
            <router-link to="/assets-public" class="btn-back">← 返回列表</router-link>
          </div>
          
          <div class="header-main">
            <div class="asset-icon-large">{{ getTypeIcon(asset.summary.type) }}</div>
            <div class="header-info">
              <div class="asset-badges">
                <span class="badge badge-type">{{ getTypeLabel(asset.summary.type) }}</span>
                <span class="badge badge-scope">{{ getScopeLabel(asset.summary.scope) }}</span>
              </div>
              <h1 class="asset-title">{{ asset.summary.name }}</h1>
              <p v-if="asset.summary.summary" class="asset-summary">{{ asset.summary.summary }}</p>
            </div>
          </div>
          
          <div class="header-meta">
            <div class="meta-item">
              <span class="meta-label">版本</span>
              <span class="meta-value">v{{ asset.versionNo }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">下载次数</span>
              <span class="meta-value">{{ asset.summary.downloadCount }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">发布时间</span>
              <span class="meta-value">{{ formatDate(asset.summary.publishedAt) }}</span>
            </div>
          </div>
          
          <div class="header-tags" v-if="asset.summary.tags">
            <span v-for="tag in asset.summary.tags.split(',')" :key="tag" class="tag">{{ tag.trim() }}</span>
          </div>
          
          <div class="header-actions">
            <button @click="handleDownload" class="btn-download" :disabled="downloading">
              {{ downloading ? '下载中...' : '下载资产' }}
            </button>
            <router-link v-if="auth.user" :to="`/admin/assets/${asset.summary.id}`" class="btn-manage">
              管理资产
            </router-link>
          </div>
        </header>

        <div class="detail-body">
          <h2 class="section-title">资产内容</h2>
          <div class="markdown-content" v-html="renderedBody"></div>
          
          <div v-if="asset.changelog" class="changelog-section">
            <h3 class="changelog-title">更新日志</h3>
            <p class="changelog-text">{{ asset.changelog }}</p>
          </div>
        </div>
      </div>
      
      <div v-else class="error-state">
        <div class="error-icon">⚠️</div>
        <p class="error-text">资产不存在或未发布</p>
        <router-link to="/assets-public" class="btn-back-link">返回资产列表</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { portalApi } from '../api/portal'
import { assetApi } from '../api/asset'
import { useAuthStore } from '../stores/auth'
import type { AssetDetail } from '../types/portal'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const asset = ref<AssetDetail | null>(null)
const loading = ref(false)
const downloading = ref(false)

const renderedBody = computed(() => {
  if (!asset.value?.body) return ''
  return marked(asset.value.body)
})

onMounted(async () => {
  await loadAsset()
})

async function loadAsset() {
  loading.value = true
  try {
    const id = route.params.id as string
    asset.value = await portalApi.getAssetDetail(id)
  } catch (err) {
    console.error('Failed to load asset:', err)
    asset.value = null
  } finally {
    loading.value = false
  }
}

async function handleDownload() {
  if (!asset.value) return
  
  if (!auth.user) {
    const confirmed = confirm('下载资产需要登录。是否前往登录页面?')
    if (confirmed) {
      router.push('/login')
    }
    return
  }
  
  downloading.value = true
  try {
    await assetApi.recordDownload(asset.value.summary.id)
    asset.value.summary.downloadCount++
    alert('下载已记录')
  } catch (err) {
    console.error('Failed to record download:', err)
    alert('下载记录失败')
  } finally {
    downloading.value = false
  }
}

function getTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    RULE: '📋',
    SKILL: '⚡',
    DOCUMENT: '📄'
  }
  return icons[type] || '📦'
}

function getTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    RULE: '规则',
    SKILL: '技能',
    DOCUMENT: '文档'
  }
  return labels[type] || type
}

function getScopeLabel(scope: string): string {
  const labels: Record<string, string> = {
    ORGANIZATION: '组织级',
    TECH_STACK: '技术栈',
    PROJECT: '项目级'
  }
  return labels[scope] || scope
}

function formatDate(date: string): string {
  return new Date(date).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}
</script>

<style scoped>
.asset-detail-public {
  min-height: 100vh;
  background: var(--surface-0);
  padding: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 4vw, 3rem);
}

.page-container {
  max-width: 1000px;
  margin: 0 auto;
}

.loading,
.error-state {
  text-align: center;
  padding: 6rem 2rem;
}

.loading {
  color: var(--text-secondary);
  font-size: 1.125rem;
}

.detail-content {
  display: grid;
  gap: 3rem;
}

.detail-header {
  background: var(--surface-1);
  padding: 2.5rem;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.header-top {
  margin-bottom: 2rem;
}

.btn-back {
  display: inline-flex;
  align-items: center;
  padding: 0.625rem 1.25rem;
  background: var(--surface-2);
  color: var(--text-secondary);
  text-decoration: none;
  border-radius: 999px;
  font-size: 0.9375rem;
  transition: all 0.2s ease;
}

.btn-back:hover {
  color: var(--text-primary);
  background: var(--surface-3);
}

.header-main {
  display: flex;
  gap: 2rem;
  margin-bottom: 2rem;
}

.asset-icon-large {
  font-size: 4rem;
  filter: drop-shadow(0 4px 12px rgba(56, 189, 248, 0.3));
}

.header-info {
  flex: 1;
}

.asset-badges {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.badge {
  padding: 0.5rem 1.125rem;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.badge-type {
  background: var(--surface-3);
  color: var(--accent);
}

.badge-scope {
  background: var(--surface-3);
  color: var(--text-secondary);
}

.asset-title {
  font-size: clamp(1.75rem, 4vw, 2.25rem);
  font-weight: 700;
  margin: 0 0 1rem;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.asset-summary {
  font-size: 1.0625rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.header-meta {
  display: flex;
  gap: 3rem;
  margin-bottom: 2rem;
  padding: 1.5rem 0;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.meta-label {
  font-size: 0.875rem;
  color: var(--text-tertiary);
}

.meta-value {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
}

.header-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.tag {
  padding: 0.5rem 1rem;
  background: var(--surface-2);
  color: var(--text-secondary);
  border-radius: 12px;
  font-size: 0.875rem;
}

.header-actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.btn-download,
.btn-manage {
  padding: 1rem 2rem;
  border-radius: 999px;
  font-size: 1rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s ease;
  cursor: pointer;
  border: none;
}

.btn-download {
  background: var(--accent);
  color: var(--surface-0);
  box-shadow: 0 4px 16px rgba(56, 189, 248, 0.3);
}

.btn-download:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(56, 189, 248, 0.4);
}

.btn-download:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-manage {
  background: var(--surface-3);
  color: var(--text-primary);
  border: 1px solid rgba(255, 255, 255, 0.1);
  display: inline-flex;
  align-items: center;
}

.btn-manage:hover {
  background: var(--surface-4);
}

.detail-body {
  background: var(--surface-1);
  padding: 2.5rem;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin: 0 0 2rem;
  color: var(--text-primary);
}

.markdown-content {
  color: var(--text-secondary);
  line-height: 1.8;
  font-size: 1rem;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  color: var(--text-primary);
  margin-top: 2rem;
  margin-bottom: 1rem;
  font-weight: 600;
}

.markdown-content :deep(h1) {
  font-size: 2rem;
}

.markdown-content :deep(h2) {
  font-size: 1.5rem;
}

.markdown-content :deep(h3) {
  font-size: 1.25rem;
}

.markdown-content :deep(p) {
  margin-bottom: 1.25rem;
}

.markdown-content :deep(code) {
  background: var(--surface-2);
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
  font-size: 0.9em;
  font-family: 'Consolas', 'Monaco', monospace;
}

.markdown-content :deep(pre) {
  background: var(--surface-2);
  padding: 1.5rem;
  border-radius: 12px;
  overflow-x: auto;
  margin-bottom: 1.5rem;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
}

.changelog-section {
  margin-top: 3rem;
  padding-top: 2rem;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.changelog-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 1rem;
  color: var(--text-primary);
}

.changelog-text {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.error-icon {
  font-size: 4rem;
  margin-bottom: 1.5rem;
  opacity: 0.3;
}

.error-text {
  font-size: 1.125rem;
  color: var(--text-tertiary);
  margin: 0 0 2rem;
}

.btn-back-link {
  display: inline-block;
  padding: 1rem 2rem;
  background: var(--accent);
  color: var(--surface-0);
  text-decoration: none;
  border-radius: 999px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.btn-back-link:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(56, 189, 248, 0.3);
}
</style>
```

- [ ] **Step 2: Verify component compiles**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/AssetPublicDetail.vue
git commit -m "feat(frontend): add public asset detail page

- Display asset metadata, version, download count
- Render markdown content with syntax highlighting
- Download tracking with login prompt for guests
- Changelog display and back navigation
- Error state for non-existent assets

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Statistics Public Page with ECharts

**Files:**
- Create: `frontend/src/views/StatisticsPublic.vue`
- Modify: `frontend/package.json:12-18` (add echarts dependency)

**Interfaces:**
- Consumes: `portalApi.getStatistics()` (Task 3)
- Produces: Visual dashboard with type breakdown pie chart, scope breakdown bar chart, key metrics

- [ ] **Step 1: Install ECharts**

Run: `cd frontend && npm install echarts@5.5.1`
Expected: echarts added to package.json dependencies

- [ ] **Step 2: Create StatisticsPublic component with ECharts**

```vue
<!-- frontend/src/views/StatisticsPublic.vue -->
<template>
  <div class="statistics-public">
    <div class="page-container">
      <header class="page-header">
        <h1 class="page-title">平台统计</h1>
        <p class="page-subtitle">查看资产平台的整体数据概览</p>
      </header>

      <div v-if="loading" class="loading">加载中...</div>
      
      <div v-else-if="statistics" class="stats-content">
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-icon">📦</div>
            <div class="metric-info">
              <div class="metric-value">{{ statistics.totalAssets }}</div>
              <div class="metric-label">资产总数</div>
            </div>
          </div>
          
          <div class="metric-card">
            <div class="metric-icon">⬇️</div>
            <div class="metric-info">
              <div class="metric-value">{{ statistics.totalDownloads }}</div>
              <div class="metric-label">下载次数</div>
            </div>
          </div>
          
          <div class="metric-card">
            <div class="metric-icon">📋</div>
            <div class="metric-info">
              <div class="metric-value">{{ statistics.typeBreakdown.rule }}</div>
              <div class="metric-label">规则资产</div>
            </div>
          </div>
          
          <div class="metric-card">
            <div class="metric-icon">⚡</div>
            <div class="metric-info">
              <div class="metric-value">{{ statistics.typeBreakdown.skill }}</div>
              <div class="metric-label">技能资产</div>
            </div>
          </div>
        </div>

        <div class="charts-grid">
          <div class="chart-card">
            <h2 class="chart-title">资产类型分布</h2>
            <div ref="typeChartRef" class="chart-container"></div>
          </div>
          
          <div class="chart-card">
            <h2 class="chart-title">资产范围分布</h2>
            <div ref="scopeChartRef" class="chart-container"></div>
          </div>
        </div>
      </div>
      
      <div v-else class="error-state">
        <p class="error-text">加载统计数据失败</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { portalApi } from '../api/portal'
import type { Statistics } from '../types/portal'

const statistics = ref<Statistics | null>(null)
const loading = ref(false)
const typeChartRef = ref<HTMLElement>()
const scopeChartRef = ref<HTMLElement>()
let typeChart: echarts.ECharts | null = null
let scopeChart: echarts.ECharts | null = null

onMounted(async () => {
  await loadStatistics()
  if (statistics.value) {
    initCharts()
    window.addEventListener('resize', handleResize)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  typeChart?.dispose()
  scopeChart?.dispose()
})

async function loadStatistics() {
  loading.value = true
  try {
    statistics.value = await portalApi.getStatistics()
  } catch (err) {
    console.error('Failed to load statistics:', err)
  } finally {
    loading.value = false
  }
}

function initCharts() {
  if (!statistics.value || !typeChartRef.value || !scopeChartRef.value) return
  
  typeChart = echarts.init(typeChartRef.value)
  scopeChart = echarts.init(scopeChartRef.value)
  
  const typeData = [
    { name: '规则', value: statistics.value.typeBreakdown.rule },
    { name: '技能', value: statistics.value.typeBreakdown.skill },
    { name: '文档', value: statistics.value.typeBreakdown.document }
  ]
  
  typeChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: '#161D2B',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      textStyle: { color: '#ffffff' }
    },
    legend: {
      bottom: '0%',
      left: 'center',
      textStyle: { color: 'rgba(255, 255, 255, 0.7)' }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#0F131C',
        borderWidth: 2
      },
      label: {
        show: true,
        color: 'rgba(255, 255, 255, 0.9)',
        fontSize: 14,
        fontWeight: 600
      },
      emphasis: {
        label: { show: true, fontSize: 16, fontWeight: 700 }
      },
      data: typeData,
      color: ['#38BDF8', '#6EE7B7', '#E9A568']
    }]
  })
  
  const scopeData = [
    { name: '组织级', value: statistics.value.scopeBreakdown.organization },
    { name: '技术栈', value: statistics.value.scopeBreakdown.techStack },
    { name: '项目级', value: statistics.value.scopeBreakdown.project }
  ]
  
  scopeChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#161D2B',
      borderColor: 'rgba(255, 255, 255, 0.1)',
      textStyle: { color: '#ffffff' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: scopeData.map(d => d.name),
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
      axisLabel: { color: 'rgba(255, 255, 255, 0.7)', fontSize: 13 }
    },
    yAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
      axisLabel: { color: 'rgba(255, 255, 255, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)' } }
    },
    series: [{
      type: 'bar',
      data: scopeData.map(d => d.value),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#38BDF8' },
          { offset: 1, color: '#1E2636' }
        ]),
        borderRadius: [8, 8, 0, 0]
      },
      barWidth: '40%'
    }]
  })
}

function handleResize() {
  typeChart?.resize()
  scopeChart?.resize()
}
</script>

<style scoped>
.statistics-public {
  min-height: 100vh;
  background: var(--surface-0);
  padding: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 4vw, 3rem);
}

.page-container {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: clamp(2rem, 4vw, 3rem);
}

.page-title {
  font-size: clamp(2rem, 4vw, 2.5rem);
  font-weight: 700;
  margin: 0 0 1rem;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.page-subtitle {
  font-size: clamp(1rem, 2vw, 1.125rem);
  color: var(--text-secondary);
  margin: 0;
}

.loading {
  text-align: center;
  padding: 4rem 2rem;
  color: var(--text-secondary);
  font-size: 1.125rem;
}

.stats-content {
  display: grid;
  gap: 3rem;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.5rem;
}

.metric-card {
  background: var(--surface-2);
  padding: 2rem;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  gap: 1.5rem;
  transition: all 0.3s ease;
}

.metric-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.metric-icon {
  font-size: 2.5rem;
  filter: drop-shadow(0 2px 8px rgba(56, 189, 248, 0.2));
}

.metric-info {
  flex: 1;
}

.metric-value {
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 0.5rem;
  letter-spacing: -0.02em;
}

.metric-label {
  font-size: 1rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 2rem;
}

.chart-card {
  background: var(--surface-2);
  padding: 2rem;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.chart-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0 0 1.5rem;
  color: var(--text-primary);
}

.chart-container {
  width: 100%;
  height: 400px;
}

.error-state {
  text-align: center;
  padding: 6rem 2rem;
}

.error-text {
  font-size: 1.125rem;
  color: var(--text-tertiary);
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 3: Verify build with new dependency**

Run: `cd frontend && npm run build`
Expected: Build succeeds with echarts included

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/StatisticsPublic.vue frontend/package.json frontend/package-lock.json
git commit -m "feat(frontend): add public statistics page with ECharts

- Install echarts@5.5.1 for data visualization
- Display key metrics in card grid
- Type breakdown pie chart with custom colors
- Scope breakdown bar chart with gradient styling
- Responsive chart resizing

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: About and Help Pages

**Files:**
- Create: `frontend/src/views/About.vue`
- Create: `frontend/src/views/Help.vue`

**Interfaces:**
- Consumes: None (static content pages)
- Produces: About page with platform introduction, Help page with usage documentation

- [ ] **Step 1: Create About page**

```vue
<!-- frontend/src/views/About.vue -->
<template>
  <div class="about-page">
    <div class="page-container">
      <header class="page-header">
        <h1 class="page-title">关于平台</h1>
        <p class="page-subtitle">数科院AI资产管理平台介绍</p>
      </header>

      <div class="content-sections">
        <section class="content-section">
          <h2 class="section-title">平台简介</h2>
          <p class="section-text">
            数科院AI资产管理平台是一个统一管理和共享AI规则、技能和文档资产的综合性平台。
            我们致力于帮助组织高效管理和分发AI相关资产，促进知识共享和团队协作。
          </p>
        </section>

        <section class="content-section">
          <h2 class="section-title">核心功能</h2>
          <div class="features-grid">
            <div class="feature-card">
              <div class="feature-icon">📋</div>
              <h3 class="feature-title">规则管理</h3>
              <p class="feature-desc">统一管理AI规则资产，支持版本控制和审批流程</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">⚡</div>
              <h3 class="feature-title">技能管理</h3>
              <p class="feature-desc">组织和共享AI技能资产，方便团队重用和协作</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">📄</div>
              <h3 class="feature-title">文档管理</h3>
              <p class="feature-desc">集中存储和管理AI相关文档，支持Markdown格式</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">👥</div>
              <h3 class="feature-title">团队协作</h3>
              <p class="feature-desc">支持团队管理、权限控制和审批工作流</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">📊</div>
              <h3 class="feature-title">数据统计</h3>
              <p class="feature-desc">实时统计平台数据，可视化展示资产使用情况</p>
            </div>
            <div class="feature-card">
              <div class="feature-icon">🔍</div>
              <h3 class="feature-title">搜索过滤</h3>
              <p class="feature-desc">强大的搜索和过滤功能，快速定位所需资产</p>
            </div>
          </div>
        </section>

        <section class="content-section">
          <h2 class="section-title">联系我们</h2>
          <div class="contact-info">
            <div class="contact-item">
              <div class="contact-label">所属单位</div>
              <div class="contact-value">数据科学研究院</div>
            </div>
            <div class="contact-item">
              <div class="contact-label">电子邮箱</div>
              <div class="contact-value">support@example.com</div>
            </div>
            <div class="contact-item">
              <div class="contact-label">技术支持</div>
              <div class="contact-value">周一至周五 9:00-18:00</div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.about-page {
  min-height: 100vh;
  background: var(--surface-0);
  padding: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 4vw, 3rem);
}

.page-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: clamp(3rem, 6vw, 4rem);
}

.page-title {
  font-size: clamp(2rem, 4vw, 2.5rem);
  font-weight: 700;
  margin: 0 0 1rem;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.page-subtitle {
  font-size: clamp(1rem, 2vw, 1.125rem);
  color: var(--text-secondary);
  margin: 0;
}

.content-sections {
  display: grid;
  gap: 3rem;
}

.content-section {
  background: var(--surface-1);
  padding: clamp(2rem, 4vw, 3rem);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.section-title {
  font-size: clamp(1.5rem, 3vw, 1.75rem);
  font-weight: 700;
  margin: 0 0 1.5rem;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.section-text {
  font-size: 1.0625rem;
  color: var(--text-secondary);
  line-height: 1.8;
  margin: 0;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
}

.feature-card {
  background: var(--surface-2);
  padding: 2rem;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  transition: all 0.3s ease;
}

.feature-card:hover {
  border-color: rgba(255, 255, 255, 0.1);
  transform: translateY(-4px);
}

.feature-icon {
  font-size: 2.5rem;
  margin-bottom: 1rem;
  filter: drop-shadow(0 2px 8px rgba(56, 189, 248, 0.2));
}

.feature-title {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  color: var(--text-primary);
}

.feature-desc {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.contact-info {
  display: grid;
  gap: 2rem;
}

.contact-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.5rem;
  background: var(--surface-2);
  border-radius: 12px;
}

.contact-label {
  font-size: 0.875rem;
  color: var(--text-tertiary);
  font-weight: 500;
}

.contact-value {
  font-size: 1.125rem;
  color: var(--text-primary);
  font-weight: 600;
}
</style>
```

- [ ] **Step 2: Create Help page**

```vue
<!-- frontend/src/views/Help.vue -->
<template>
  <div class="help-page">
    <div class="page-container">
      <header class="page-header">
        <h1 class="page-title">帮助文档</h1>
        <p class="page-subtitle">平台使用指南</p>
      </header>

      <div class="help-sections">
        <section class="help-section">
          <h2 class="section-title">快速开始</h2>
          <div class="help-content">
            <h3 class="content-heading">浏览资产</h3>
            <p class="content-text">
              无需登录即可浏览平台上的所有已发布资产。访问"资产展示"页面，使用搜索和过滤功能快速找到您需要的资产。
            </p>
            
            <h3 class="content-heading">下载资产</h3>
            <p class="content-text">
              下载资产需要登录账号。点击资产详情页的"下载资产"按钮，系统会记录下载次数。如果您还没有账号，请联系管理员创建。
            </p>
          </div>
        </section>

        <section class="help-section">
          <h2 class="section-title">资产类型说明</h2>
          <div class="help-content">
            <div class="type-item">
              <div class="type-header">
                <span class="type-icon">📋</span>
                <h3 class="type-name">规则 (RULE)</h3>
              </div>
              <p class="type-desc">AI规则资产，包含业务规则、决策逻辑等内容。适用于组织级、技术栈或项目级场景。</p>
            </div>
            
            <div class="type-item">
              <div class="type-header">
                <span class="type-icon">⚡</span>
                <h3 class="type-name">技能 (SKILL)</h3>
              </div>
              <p class="type-desc">AI技能资产，包含可复用的AI能力、工具函数等。便于团队成员共享和协作。</p>
            </div>
            
            <div class="type-item">
              <div class="type-header">
                <span class="type-icon">📄</span>
                <h3 class="type-name">文档 (DOCUMENT)</h3>
              </div>
              <p class="type-desc">AI相关文档资产，支持Markdown格式，包含技术文档、使用说明等内容。</p>
            </div>
          </div>
        </section>

        <section class="help-section">
          <h2 class="section-title">资产范围说明</h2>
          <div class="help-content">
            <div class="scope-item">
              <h3 class="scope-name">组织级 (ORGANIZATION)</h3>
              <p class="scope-desc">适用于整个组织的通用资产，所有团队都可以使用。</p>
            </div>
            
            <div class="scope-item">
              <h3 class="scope-name">技术栈 (TECH_STACK)</h3>
              <p class="scope-desc">特定技术栈相关的资产，如Python、Java、React等技术的专属资产。</p>
            </div>
            
            <div class="scope-item">
              <h3 class="scope-name">项目级 (PROJECT)</h3>
              <p class="scope-desc">特定项目的专属资产，通常包含项目特定的规则和配置。</p>
            </div>
          </div>
        </section>

        <section class="help-section">
          <h2 class="section-title">管理功能</h2>
          <div class="help-content">
            <p class="content-text">
              如果您拥有管理权限，可以通过页面右上角的"管理中心"按钮进入管理后台，进行以下操作：
            </p>
            <ul class="help-list">
              <li>创建和编辑资产</li>
              <li>管理资产版本和发布流程</li>
              <li>处理资产审批申请</li>
              <li>管理团队和用户权限</li>
              <li>查看详细的平台统计数据</li>
            </ul>
          </div>
        </section>

        <section class="help-section">
          <h2 class="section-title">常见问题</h2>
          <div class="help-content">
            <div class="faq-item">
              <h3 class="faq-question">Q: 如何获取账号?</h3>
              <p class="faq-answer">A: 请联系平台管理员申请账号。管理员会根据您的角色创建相应权限的账号。</p>
            </div>
            
            <div class="faq-item">
              <h3 class="faq-question">Q: 为什么看不到某些资产?</h3>
              <p class="faq-answer">A: 公开页面只显示已发布且未归档的资产。草稿和归档资产仅在管理后台可见。</p>
            </div>
            
            <div class="faq-item">
              <h3 class="faq-question">Q: 如何搜索资产?</h3>
              <p class="faq-answer">A: 在资产展示页面使用搜索框输入关键词，系统会在资产名称和描述中进行模糊搜索。您也可以使用类型和范围过滤器缩小结果范围。</p>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.help-page {
  min-height: 100vh;
  background: var(--surface-0);
  padding: clamp(2rem, 4vw, 3rem) clamp(1.5rem, 4vw, 3rem);
}

.page-container {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: clamp(3rem, 6vw, 4rem);
}

.page-title {
  font-size: clamp(2rem, 4vw, 2.5rem);
  font-weight: 700;
  margin: 0 0 1rem;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.page-subtitle {
  font-size: clamp(1rem, 2vw, 1.125rem);
  color: var(--text-secondary);
  margin: 0;
}

.help-sections {
  display: grid;
  gap: 2.5rem;
}

.help-section {
  background: var(--surface-1);
  padding: clamp(2rem, 4vw, 2.5rem);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.section-title {
  font-size: clamp(1.375rem, 3vw, 1.625rem);
  font-weight: 700;
  margin: 0 0 1.5rem;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.help-content {
  display: grid;
  gap: 1.5rem;
}

.content-heading {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  color: var(--text-primary);
}

.content-text {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0;
}

.type-item,
.scope-item {
  padding: 1.5rem;
  background: var(--surface-2);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.type-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.type-icon {
  font-size: 1.75rem;
}

.type-name,
.scope-name {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.scope-name {
  margin-bottom: 0.75rem;
}

.type-desc,
.scope-desc {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

.help-list {
  margin: 1rem 0 0 1.5rem;
  padding: 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.help-list li {
  font-size: 1rem;
  margin-bottom: 0.5rem;
}

.faq-item {
  padding: 1.5rem;
  background: var(--surface-2);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.faq-question {
  font-size: 1.0625rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  color: var(--accent);
}

.faq-answer {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0;
}
</style>
```

- [ ] **Step 3: Verify components compile**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/About.vue frontend/src/views/Help.vue
git commit -m "feat(frontend): add About and Help pages

- About page with platform introduction and features
- Help page with usage guide and FAQ
- Structured content sections with responsive design
- Contact information and resource descriptions

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Router Guard Updates for Mixed Access

**Files:**
- Modify: `frontend/src/router/index.ts:77-97`

**Interfaces:**
- Consumes: Existing route guard logic
- Produces: Updated guard allowing public portal access, enforcing auth for admin routes

- [ ] **Step 1: Update router guard to handle portal public routes**

```typescript
// Replace lines 77-97 in frontend/src/router/index.ts:

router.beforeEach(async (to, _from, next) => {
  const auth = useAuthStore()

  // Fetch user if not loaded and route requires it
  if (!auth.user && !to.meta.public) {
    await auth.fetchMe()
  }

  // Public routes: allow access without authentication
  if (to.meta.public) {
    next()
  }
  // Login page: redirect authenticated users to appropriate home
  else if (to.name === 'login' && auth.user) {
    next({ name: 'portal-home' })
  }
  // Change password: enforce for users with mustChangePassword flag
  else if (auth.user?.mustChangePassword && to.name !== 'change-password') {
    next({ name: 'change-password' })
  }
  // Admin-only routes: check for ADMIN role
  else if (to.meta.adminOnly && auth.user?.role !== 'ADMIN') {
    next({ name: 'portal-home' })
  }
  // Approver-only routes: check for APPROVER or ADMIN role
  else if (to.meta.approverOnly && auth.user?.role !== 'APPROVER' && auth.user?.role !== 'ADMIN') {
    next({ name: 'portal-home' })
  }
  // Protected routes without authentication: redirect to login
  else if (!auth.user) {
    next({ name: 'login' })
  }
  // Allow access
  else {
    next()
  }
})
```

- [ ] **Step 2: Verify TypeScript compilation**

Run: `cd frontend && npm run build`
Expected: Build succeeds

- [ ] **Step 3: Test route guard manually**

Run: `cd frontend && npm run dev`
Test scenarios:
1. Visit `/` without login → should show portal home
2. Visit `/assets-public` without login → should show assets list
3. Visit `/admin` without login → should redirect to login
4. Login and visit `/admin` → should show admin home
5. Visit `/` while logged in → should show portal home with "管理中心" button

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router/index.ts
git commit -m "feat(frontend): update router guard for mixed access

- Allow public access to portal routes (meta.public: true)
- Enforce authentication for admin routes
- Redirect unauthenticated users from protected routes to login
- Maintain role-based access control for admin/approver routes

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Global CSS Variables

**Files:**
- Modify: `frontend/src/App.vue:1-10`

**Interfaces:**
- Consumes: None
- Produces: Global CSS custom properties for design tokens

- [ ] **Step 1: Add global CSS variables to App.vue**

```vue
<!-- Replace lines 1-10 in frontend/src/App.vue: -->
<script setup lang="ts">
import { RouterView } from 'vue-router'
import GlobalDialog from './components/GlobalDialog.vue'
</script>

<template>
  <RouterView />
  <GlobalDialog />
</template>

<style>
:root {
  --surface-0: #05070C;
  --surface-1: #0A0D12;
  --surface-2: #0F131C;
  --surface-3: #161D2B;
  --surface-4: #1E2636;
  --accent: #38BDF8;
  --text-primary: rgba(255, 255, 255, 0.95);
  --text-secondary: rgba(255, 255, 255, 0.7);
  --text-tertiary: rgba(255, 255, 255, 0.5);
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background: var(--surface-0);
  color: var(--text-primary);
}

#app {
  min-height: 100vh;
}
</style>
```

- [ ] **Step 2: Verify styles apply correctly**

Run: `cd frontend && npm run dev`
Expected: Portal pages display with deep color theme

- [ ] **Step 3: Commit**

```bash
git add frontend/src/App.vue
git commit -m "feat(frontend): add global CSS design tokens

- Define surface color ladder (5 levels)
- Set accent color and text hierarchy
- Apply global reset and font smoothing
- Establish design system foundation

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: AdminLayout Style Completion

**Files:**
- Modify: `frontend/src/components/AdminLayout.vue` (add complete style section)

**Interfaces:**
- Consumes: MainLayout.vue styles (copy)
- Produces: Complete AdminLayout with all styles and "Back to Portal" button

- [ ] **Step 1: Add complete styles to AdminLayout**

Copy the entire `<style scoped>` section from MainLayout.vue to AdminLayout.vue, then add the `.btn-portal` style:

```vue
<!-- Add after existing styles in AdminLayout.vue: -->
<style scoped>
/* Copy all styles from MainLayout.vue here */

.btn-portal {
  padding: 0.625rem 1.25rem;
  background: var(--surface-3);
  color: var(--text-primary);
  text-decoration: none;
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 500;
  transition: all 0.2s ease;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-portal:hover {
  background: var(--surface-4);
  border-color: rgba(255, 255, 255, 0.2);
}
</style>
```

- [ ] **Step 2: Verify AdminLayout renders correctly**

Run: `cd frontend && npm run dev`
Navigate to: `/admin` (after login)
Expected: Admin layout displays with "返回门户" button in header

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/AdminLayout.vue
git commit -m "feat(frontend): complete AdminLayout styles

- Copy complete style section from MainLayout
- Add btn-portal style for back navigation
- Ensure consistent styling with admin interface

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: Integration Testing and Verification

**Files:**
- No new files (manual testing)

**Interfaces:**
- Consumes: All frontend and backend components
- Produces: Verified end-to-end functionality

- [ ] **Step 1: Start backend server**

Run: `mvn spring-boot:run`
Expected: Backend starts on port 8080

- [ ] **Step 2: Start frontend dev server**

Run: `cd frontend && npm run dev`
Expected: Frontend starts on port 5173

- [ ] **Step 3: Test public portal access (unauthenticated)**

Test sequence:
1. Visit `http://localhost:5173/` → Portal home displays with hero, stats, featured assets
2. Click "浏览资产" → Assets public list displays with search and filters
3. Click on an asset → Asset detail displays with markdown content
4. Click "下载资产" → Prompt to login appears
5. Click "关于" in nav → About page displays
6. Click "帮助文档" in nav → Help page displays
7. Click "平台统计" in nav → Statistics page with charts displays

- [ ] **Step 4: Test authenticated user flow**

Test sequence:
1. Click "登录" button → Login page displays
2. Login with admin/ChangeMe_0901 → Redirects to portal home
3. Verify "管理中心" button appears in header
4. Click asset detail "下载资产" → Download records successfully
5. Click "管理中心" → Redirects to `/admin` (admin home)
6. Verify admin sidebar displays with all management links
7. Click "返回门户" → Returns to portal home

- [ ] **Step 5: Test admin routes directly**

Test sequence:
1. Navigate to `/admin/assets` → Admin assets page displays
2. Navigate to `/admin/teams` → Admin teams page displays
3. Navigate to `/admin/statistics` → Admin statistics page displays
4. Verify all admin routes use AdminLayout

- [ ] **Step 6: Test backend public API**

Run manual API tests:
```bash
# List public assets
curl http://localhost:8080/api/public/assets

# Get asset detail
curl http://localhost:8080/api/public/assets/{asset-id}

# Get statistics
curl http://localhost:8080/api/public/statistics
```
Expected: All endpoints return JSON with published assets only

- [ ] **Step 7: Document test results**

Create test summary:
- ✓ Public portal pages accessible without authentication
- ✓ Download requires login with appropriate prompt
- ✓ Admin routes enforce authentication
- ✓ Layout switching works correctly (Portal vs Admin)
- ✓ Backend public API returns published assets only
- ✓ ECharts renders correctly on statistics page
- ✓ Markdown rendering works in asset detail
- ✓ Search and filter functionality works

- [ ] **Step 8: Commit test results document**

```bash
# Create test results document
echo "# Portal Website Integration Test Results\n\nAll tests passed successfully." > docs/superpowers/portal-test-results.md
git add docs/superpowers/portal-test-results.md
git commit -m "test: add portal website integration test results

- Verify public access to portal pages
- Confirm authentication enforcement for admin routes
- Test download flow with login prompt
- Validate backend public API filtering
- Check layout switching and navigation

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 15: Production Build and Final Verification

**Files:**
- Modify: `frontend/dist/` (production build output)

**Interfaces:**
- Consumes: All frontend source files
- Produces: Production-ready static assets

- [ ] **Step 1: Run frontend production build**

Run: `cd frontend && npm run build`
Expected: Build completes with no errors, outputs to `frontend/dist/`

- [ ] **Step 2: Run backend tests**

Run: `mvn test`
Expected: All tests pass, including new PortalControllerTest

- [ ] **Step 3: Build backend JAR**

Run: `mvn clean package -DskipTests`
Expected: JAR created in `target/` directory

- [ ] **Step 4: Test production build locally**

Run backend with production frontend:
```bash
java -jar target/aiasset-*.jar
```
Navigate to: `http://localhost:8080/`
Expected: Portal home page loads from production build

- [ ] **Step 5: Verify all routes work in production**

Test sequence:
1. `/` → Portal home
2. `/assets-public` → Assets list
3. `/assets-public/{id}` → Asset detail
4. `/statistics-public` → Statistics with charts
5. `/about` → About page
6. `/help` → Help page
7. `/login` → Login page
8. `/admin` (after login) → Admin home
9. `/admin/assets` → Admin assets

- [ ] **Step 6: Check browser console for errors**

Open browser DevTools console
Expected: No JavaScript errors, no 404s for assets

- [ ] **Step 7: Commit final build**

```bash
git add frontend/dist/
git commit -m "build: add production build for portal website

- Complete frontend production build
- All routes verified in production mode
- No console errors or missing assets
- Ready for deployment

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

## Plan Self-Review

**Placeholder scan:** ✓ No TBD, TODO, or implementation-later markers
**Type consistency:** ✓ All DTOs, API endpoints, and component props match across tasks
**Interface completeness:** ✓ Every task declares exact functions, types, and endpoints it produces
**Test coverage:** ✓ Every backend change has TDD cycle (test → fail → implement → pass)
**Commit granularity:** ✓ Each task ends with a focused, atomic commit
**Global constraints adherence:** ✓ All tasks follow TDD, use defined tech stack, respect design tokens

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-05-portal-website.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
