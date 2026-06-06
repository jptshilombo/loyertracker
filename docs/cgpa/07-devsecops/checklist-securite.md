# Checklist sécurité (DevSecOps) — LoyerTracker

> Renseignée au **Gate 6 (DevSecOps)** — état au 2026-06-06 (issue des étapes 01→09).
> Légende : `[x]` fait · `[ ]` non fait. Statut : ✅ couvert · 🟠 partiel · ⏭️ différé (réserve datée au Gate 6).

## 1. Conception (Shift-Left)
- [x] ✅ Analyse de risques / surface d'attaque réalisée — phase 05 (DAT, 8 ADR) : point d'entrée unique Nginx (ADR-08), cloisonnement 3 couches (ADR-01).
- [x] ✅ Données sensibles identifiées et classifiées (RGPD) — ADR-03 (RGPD) ; identité locataire = donnée sensible (ENF-03).
- [x] ✅ Principe du moindre privilège — RLS PostgreSQL (`ENABLE`+`FORCE`), GUC `app.current_bailleur_id` fail-closed, rôle technique `loyertracker_batch` `BYPASSRLS NOLOGIN`, resource-server bearer-only sans secret.

## 2. Authentification & autorisation
- [x] ✅ Authentification centralisée (Keycloak / OIDC / OAuth2) — realm `loyertracker` versionné, SPA en OIDC **PKCE**, API resource-server JWT.
- [x] ✅ Rôles et scopes appliqués côté API — `SecurityConfig` (liste blanche health/info + invitation), `@EnableMethodSecurity`/`@PreAuthorize`, rôles via `realm_access.roles` (RBAC grossier ADR-02) + ReBAC applicatif + RLS.
- [x] 🟠 Sessions / tokens sécurisés — API **stateless** (aucune session), JWT bearer, expiration/refresh gérés par Keycloak, TLS obligatoire. 🟠 *Dette US-10* : migrer vers l'API fonctionnelle keycloak-angular et **valider le flux OIDC/PKCE en runtime** (tests actuels mockés) — réserve R6.

## 3. Gestion des secrets
- [x] ✅ Aucun secret en clair dans le dépôt — Gitleaks CI = 0 secret sur dépôt + historique ; `git log --all -- '**/.env'` = 0.
- [x] 🟠 Secrets gérés via coffre / variables sécurisées — `.env` (dev) + GitHub Actions Secrets (CI) + env hôte (prod). ⏭️ Coffre (Vault/Infisical) hors périmètre MVP — réserve R2.
- [x] 🟠 Rotation des secrets prévue — procédure **manuelle documentée** (README, `openssl rand`). ⏭️ Rotation automatisée différée — réserve R5.

## 4. Code & dépendances
- [ ] ⏭️ SAST (analyse statique) intégré à la CI — **absent** (ni CodeQL, ni Semgrep, ni SpotBugs) — **réserve R1 (prioritaire)**.
- [x] ✅ SCA (vulnérabilités des dépendances) intégré — Trivy `fs` (npm, bloquant) + Trivy image (Java, bloquant) + OWASP Dependency-Check (informatif).
- [x] ✅ Scan de secrets dans la CI — Gitleaks (dépôt + historique) + Trivy `secret` sur l'image.
- [x] ✅ Vulnérabilités critiques traitées avant fusion — **démontré** : Spring Boot 3.3.5→3.5.14 (+ overrides Tomcat/pgjdbc) ; Angular 18→19.2.25 (7 CVE HIGH XSS/XSRF). Politique `CRITICAL,HIGH` `exit-code 1`.

## 5. Conteneurs & infrastructure
- [x] ✅ Images Docker scannées — Trivy `vuln,secret`, `severity CRITICAL,HIGH`, `ignore-unfixed`, gate bloquant.
- [x] ✅ Images basées sur des sources de confiance, à jour — images officielles Alpine (eclipse-temurin JRE 21, nginx 1.27, postgres 16, keycloak 24) ; dépendances montées suite aux scans.
- [x] 🟠 Conteneurs non-root, surface minimale — **API non-root** (`USER app`, JRE Alpine), multi-stage (pas de toolchain de build dans l'image finale). 🟠 Image **web (Nginx)** = master root par défaut → passer à `nginxinc/nginx-unprivileged` — réserve R5.

## 6. Communication & données
- [x] ✅ HTTPS / TLS partout — Nginx point d'entrée unique 443 (TLS 1.2/1.3), redirection 80→443, **HSTS**, en-têtes `X-Content-Type-Options`/`Referrer-Policy`/`X-Frame-Options`/CSP de base.
- [ ] ⏭️ Chiffrement des données sensibles au repos — volume PostgreSQL non chiffré (à traiter au niveau infra/prod) — réserve R5.
- [x] 🟠 Validation/échappement des entrées (OWASP Top 10) — sécurités cadre en place (CORS restreint, CSRF désactivé car API stateless à jeton, CSP, sanitization Angular). 🟠 Validation applicative (Bean Validation) à ajouter avec les endpoints métier — phase 08.

## 7. Journalisation & supervision
- [x] 🟠 Logs de sécurité (auth, erreurs) — `org.springframework.security` à INFO, logs structurés JSON ECS sur stdout, prêts pour centralisation. ⏭️ Centralisation/corrélation non déployées — réserve R4.
- [ ] ⏭️ Alerting sur événements sensibles — non — réserve R4.
- [x] ✅ Pas de données sensibles dans les logs — règle **ENF-03** inscrite en config, niveau INFO, aucun log de token (décodeur JWT) ; aucune logique métier ne journalise encore de PII.

## 8. Conformité
- [x] ✅ Conformité RGPD vérifiée — ADR-03 (minimisation, finalité, droits) ; classification des données sensibles faite.
- [ ] ⏭️ Politique de sauvegarde/restauration en place — différée (pas de prod) — réserve R5.

---
*Checklist CGPA v1.0 — Phase 07 (DevSecOps). Réserves R1→R6 détaillées dans `gate-6-decision.md`.*
