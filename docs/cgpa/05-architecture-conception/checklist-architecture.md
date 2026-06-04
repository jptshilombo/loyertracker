# Checklist architecture — LoyerTracker

> Déroulée au **Gate 4** (dernier verrou avant développement). Réf. : `dossier-architecture.md` + `adr/`.

## 1. Traçabilité
- [x] Chaque exigence du CDC est couverte par l'architecture — *composants ↔ EF/ENF (DAT §2, §4)*
- [x] Exigences non fonctionnelles adressées (perf, dispo, scalabilité) — *DAT §3.3 index perf, §6 déploiement/backup*
- [x] Décisions documentées (ADR) — *7 ADR (DAT §7 + adr/ADR-01..03)*

## 2. Structure
- [x] Composants et responsabilités clairement définis — *DAT §2*
- [x] Couplage maîtrisé / séparation des préoccupations — *monolithe modulaire, packages par domaine (ADR-06)*
- [x] Modèle de données défini — *DAT §3 (MCD, attributs, index)*
- [x] Contrats d'API spécifiés — *DAT §4 (OpenAPI figé en Phase 07)*

## 3. Sécurité by design
- [x] Authentification/autorisation conçue (Keycloak/OIDC) — *ADR-02 (AuthN Keycloak + ReBAC app)*
- [x] Gestion des secrets prévue — *DAT §5.3 (hors dépôt, scan CI)*
- [x] Surface d'attaque évaluée — *DAT §5.4 (intégrations externes nulles)*
- [x] Conformité (RGPD) prise en compte — *ADR-03 (pseudonymisation)*

## 4. Déploiement
- [x] Conteneurisation définie (Docker) — *DAT §6, ENF-09*
- [x] Environnements `dev`/`staging`/`prod` prévus — *DAT §6 (image unique, config externalisée)*
- [x] Stratégie de déploiement et scalabilité définies — *DAT §6 (recreate/rolling, backup ENF-07)*

## 5. Qualité & évolutivité
- [x] Choix technologiques justifiés (stack Spring Boot/Angular) — *ADR-06, contraintes CDC §6*
- [x] Points d'extension / évolutivité identifiés — *modularité par domaine ; exclusions MVP réactivables (PDF, IRL, e-mail…)*
- [x] Risques techniques et mitigations documentés — *DAT §8*

## 6. Observabilité
- [x] Stratégie de logs et de monitoring prévue — *AuditLog (écritures) + logs applicatifs ; monitoring batch (run/échec rattrapé)*
- [ ] Points de mesure (KPI techniques) définis — *à outiller en Phase 07 (latence dashboard P95 < 2 s, taux de succès batch) ; non bloquant Gate 4*

---
**Synthèse :** 17/18 items cochés. Le seul item ouvert (KPI techniques outillés) relève de l'implémentation/CI (Phases 06–07) et ne bloque pas le Gate 4. → cohérent avec l'axe *Automatisation* 2/4 du DAT.
