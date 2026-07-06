# Gate Production — Sprint 11 (EP-14a Quittances certifiées) + Sprint Angular 20→22

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Type | Release **MINOR candidate** — Sprint 11 EP-14a (US-99→102, quittance certifiée) + Sprint Angular 20→22 (frontend) |
| Version | **Aucune ne peut être attribuée** — voir §4 |
| Commit applicatif candidat | `9713fdaa7d82a002f20f5859835893d95aa6be17` (`origin/main` HEAD) |
| Tag candidat | `sha-9713fdaa` |
| Environnement source | Staging `ai-test-server` — `STAGING_DEPLOYED` (`gate-staging-sprint11-ep14a-v5.4.1-decision.md`) |
| Production actuelle | `1.8.0` — `sha-2c5f43c7` (LIVE depuis 2026-07-04) |
| Rollback disponible | `sha-2c5f43c7` (tag Production actuel, sans changement à annuler puisque ce Gate ne conclut pas à un déploiement) |
| Décision | **NO GO — bloqué par construction (ADR-15 K5), pas par anomalie découverte** |
| Statut | `PRODUCTION_READY` **non atteint** |

## 1. Objet

Statuer l'autorisation de promotion Production du candidat `sha-9713fdaa`, actuellement
`STAGING_DEPLOYED` (Gate Staging GO du 2026-07-06, smoke 59/0, STG-ISOL-01 PASS, rendu PDF
certifié vérifié visuellement).

**Ce document ne peut pas conclure à un GO, et ne le pourra pas tant que le Sprint 12 (EP-14b)
n'est pas livré.** Contrairement au Gate Production Sprint 9 (`1.7.0`, NO GO pour anomalie de
données *découverte* pendant l'instruction), ce blocage n'est pas une découverte : il a été
**décidé et tracé dès le cadrage du lot**, au kickoff EP-14 (ADR-15, point **K5**, 2026-07-05) —
« 2 sprints (11 = socle certifié + PDF ; 12 = vérification publique + observabilité), **release
Production unique `1.9.0`** — le QR imprimé au Sprint 11 ne va en Production qu'avec la page de
vérification opérationnelle ». Ce Gate est instruit pour **documenter formellement l'état** et
clarifier une conséquence structurelle non encore explicitée : l'empilement des sprints sur `main`.

## 2. Périmètre

**Sprint 11 EP-14a — quittance certifiée** (PR #183, merge `eddc037`, 2026-07-05)
- Migration **V22** : `quittance` versionnée, `quittance_numerotation` (par bailleur+année),
  `quittance_verification_log`, RLS `FORCE`.
- `QuittanceCertifieeService` (émission idempotente, numéro `QT-YYYY-NNNNNN` jamais réutilisé),
  `TokenQuittanceService` (HMAC-SHA256 + `token_kid`), QR ZXing, gabarit PDF A4 professionnel.
- Extension export RGPD (métadonnées + contenu canonique).
- **Le QR imprimé sur chaque quittance pointe vers `https://loyertracker.loyerpro.org/verify/receipt/{id}`
  — cette route publique n'existe pas encore côté API ni frontend** (livrée au Sprint 12).

**Sprint Angular 20 → 22** (PR #197, merge `05a4f86`, 2026-07-06)
- `@angular/*` (8 paquets) + cli/build-angular/compiler-cli → 22.0.5, `typescript` → 6.0.3,
  `angular-eslint` → 22.0.0, `keycloak-angular` → 22.0.0.
- Cadrage préalable GO PO (`plan-execution-upgrade-angular-22.md`), critères GO §4 validés
  (build/lint/tests/vérification navigateur `OnPush`), CI verte 7/7.
- **Aucun blocage connu propre à ce sprint** — voir §4.3 pour la conséquence de son empilement
  après Sprint 11 sur `main`.

**Correctifs CI/documentation** (PR #194 CodeQL init/analyze, PR #195/#196/#198/#199 docs-only) :
sans impact applicatif, sans rapport avec ce Gate.

**Exclu (Sprint 12, EP-14b, non démarré)** : `GET /api/verify/receipt/{id}` (endpoint public,
sans authentification, `SECURITY DEFINER` déjà préparé par V22), page Angular publique
`/verify/receipt/:id`, observabilité dédiée, rate-limit Nginx sur la route publique.

## 3. Checklist Gate Production (CGPA v5.4.1)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre identifié | ✅ | §2 |
| Version SemVer identifiée | ❌ | Aucune — voir §4 (le candidat ne correspond à aucune release publiable en l'état) |
| Commit et artefact identifiés | ✅ | `9713fdaa`, tag GHCR `sha-9713fdaa` (api + web, confirmés présents) |
| Environnement source | ✅ | Staging `ai-test-server`, `STAGING_DEPLOYED` |
| Environnement cible | ✅ | Production `loyertracker-prod-server` |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ✅ | `sha-9713fdaa` déployé le 2026-07-06 |
| Services Staging | ✅ | 8/8 conteneurs `Up`/`(healthy)`, restart=0 |
| Smoke Staging | ✅ | 59 PASS / 0 FAIL |
| Flyway Staging | ✅ | 22/22 — V22 appliquée sans erreur |
| `STG-ISOL-01` | ✅ **PASS** | Avant et après déploiement (`gate-staging-sprint11-ep14a-v5.4.1-decision.md` §5) |
| Vérification manuelle du rendu PDF certifié | ✅ **PASS** | Quittance réelle téléchargée et rendue conforme (logo, badge certifié, QR, mentions légales) |
| Vérification navigateur Angular 22 (`OnPush`) | ✅ **PASS** | Dashboard bailleur, rechargement async confirmé fonctionnel |
| Accumulation Staging | ⚠️ | Aucun commit applicatif postérieur à `sha-9713fdaa` — candidat figé, mais **empile deux sprints distincts** (§4.3) |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Sprint 11 validé | ✅ | Clôture tracée (PR #183, `project-state.md` 2026-07-05) |
| Sprint Angular 22 validé | ✅ | GO PO du cadrage + critères GO tous verts (PR #197) |
| Release notes disponibles | ❌ | Non rédigées — sans objet tant qu'aucune version n'est attribuable (§4) |
| Changelog disponible | ⚠️ | `CHANGELOG.md` `[Non publié]` couvre Sprint 11 ; à vérifier qu'il ne mélange pas des sprints déjà réservés à des releases distinctes avant toute rédaction de release notes |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | `9713fda` — CodeQL/Backend/Frontend/Sécurité/Packaging Docker tous SUCCESS |
| Tests backend | ✅ | `mvn verify` 156/156 (Sprint 11) |
| Tests frontend | ✅ | `ng test` 85/85 (post-bump Angular 22) |
| Gitleaks / SCA / Trivy | ✅ | SUCCESS sur PR #183 et #197 |
| Migrations Production | ⚠️ | V22 prête (additive, rollback applicatif seul), **mais sans objet tant que ce Gate ne peut pas conclure GO** |
| Secrets Production | ⚠️ **Action requise si ce Gate progresse un jour** | `QUITTANCE_HMAC_SECRET` devra être généré et injecté au `.env` Production (distinct de celui de Staging) — non fait, hors périmètre d'un Gate NO GO |
| Observabilité | ✅ | Dispositif Production existant inchangé pour ce périmètre |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback | ✅ | `sha-2c5f43c7` (`1.8.0` actuel) — sans objet, aucun déploiement engagé par ce Gate |
| Migration V22 et rollback | ✅ | Additive (nouvelles tables/colonnes uniquement) — rollback applicatif seul viable, cohérent avec le critère GO acté au Sprint 11 |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager (si ce Gate progresse un jour) |

## 4. Analyse — blocage structurel par construction (non une anomalie découverte)

### 4.1 Rappel de la décision source

ADR-15, point **K5**, tranché par le PO le 2026-07-05, **avant même le démarrage du Sprint 11** :
le découpage en 2 sprints est délibéré, et la release Production est explicitement **unique**
(`1.9.0`), regroupant Sprint 11 **et** Sprint 12. La raison est fonctionnelle et non contournable
sans dégrader le produit livré : chaque quittance certifiée imprime un QR code pointant vers
`https://loyertracker.loyerpro.org/verify/receipt/{id}` (vérifié dans le rendu PDF, §Staging) ;
tant que cette route n'existe pas côté Production, **scanner ce QR renverrait une erreur 404** pour
tout locataire ou tiers vérifiant l'authenticité d'une quittance reçue — un document *certifié*
dont le mécanisme de vérification imprimé est cassé.

### 4.2 Confirmation — le blocage est toujours effectif

Vérifié à l'instruction de ce Gate (2026-07-06) : Sprint 12 (EP-14b) n'a pas démarré. Le plan
d'exécution correspondant est déjà approuvé (PR #182) mais aucun code n'a été produit. Le blocage
K5 s'applique donc intégralement, sans changement depuis le Sprint 11.

### 4.3 Conséquence structurelle non encore explicitée : l'empilement Angular 22

Le Sprint 11 (`eddc037`, 2026-07-05) a été mergé sur `main` **avant** le démarrage du sprint
Angular 20→22 (`05a4f86`, 2026-07-06). Ces deux sprints sont donc **inséparables dans l'historique
`main`** : tout déploiement Production depuis `main` HEAD embarquerait nécessairement les deux, ou
aucun des deux.

**Conséquence pour le Product Owner, à trancher explicitement :** l'upgrade Angular 22 — qui,
seul, ne présente **aucun blocage connu** (Staging PASS, critères GO tous verts) — se retrouve
elle aussi bloquée en Production tant que le verrou K5 (Sprint 11/12) n'est pas levé, **sauf** si
le PO souhaite un traitement exceptionnel (option C ci-dessous). Ce n'est pas un défaut technique,
c'est une conséquence de l'ordre de fusion sur `main` combinée à la gouvernance de release choisie
au Sprint 11.

### 4.4 Options (aucune n'est retenue par ce document)

| Option | Description | Délai | Risque |
|---|---|---|---|
| **A — Attendre le Sprint 12** (recommandée, cohérente avec ADR-15 K5) | Cadrer et exécuter le Sprint 12 (plan déjà approuvé, PR #182), puis instruire un Gate Production unique `1.9.0` couvrant Sprint 11 + 12 + Angular 22 | Dépend de la durée du Sprint 12 | Aucun — c'est la trajectoire déjà actée par le PO |
| **B — Statu quo prolongé** | Ne rien déployer, `1.8.0` reste seule en Production, aucune action | Nul | Angular 22 et les quittances certifiées restent indisponibles en Production plus longtemps que nécessaire pour l'upgrade Angular (qui n'a pourtant aucun blocage propre) |
| **C — Cherry-pick exceptionnel Angular 22 seul** | Créer une branche depuis `sha-2c5f43c7` (`1.8.0`), rebaser/appliquer uniquement le diff Angular 22 (sans Sprint 11), Gate Production dédié distinct | Effort non négligeable (résolution de conflits probable, nouvelle CI complète, nouveau Gate Staging) | Écart de gouvernance à qualifier explicitement (contourne l'ordre naturel de `main`) ; à ne considérer que si le PO juge l'upgrade Angular urgente indépendamment des quittances |

## 5. Réserves et conditions

| ID | Nature | Sévérité | Traitement |
|----|--------|----------|------------|
| **RSV-PROD-EP14-01** | QR de vérification des quittances certifiées pointe vers une route Production inexistante (`/verify/receipt/:id`, Sprint 12 non livré) | **Bloquant, par construction (ADR-15 K5)** | Nécessite la livraison complète du Sprint 12 avant toute promotion Production de ce périmètre |
| **RSV-PROD-EP14-02** (nouvelle, identifiée par ce Gate) | Le sprint Angular 20→22, sans blocage propre, est empilé après Sprint 11 sur `main` et hérite donc du blocage K5 | Non bloquant en soi (aucun défaut Angular 22) — **arbitrage PO requis uniquement si l'option C (§4.4) est envisagée** | À trancher par le PO : attendre 1.9.0 (option A, par défaut) ou cadrer un cherry-pick exceptionnel (option C) |
| — | `QUITTANCE_HMAC_SECRET` Production non généré | Non bloquant pour cette analyse (aucun déploiement engagé) | À traiter au Préflight, si et quand ce Gate progresse |

## 6. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **NO GO** — conforme à la décision de gouvernance déjà actée (ADR-15 K5) ; aucune tentative de contournement identifiée dans le code ou les commits |
| Enterprise Architect | **NO GO** — un document certifié dont le mécanisme de vérification imprimé (QR) est cassé en Production constituerait une régression de confiance, cohérent avec le refus déjà tranché au cadrage |
| DevSecOps Lead | **Techniquement recevable** (CI verte, smoke 59/0, STG-ISOL-01 PASS) — mais **NO GO** relève d'une décision de portée produit (K5), pas d'un défaut technique |
| Release Manager | **NO GO** — aucune version SemVer n'est attribuable à ce périmètre partiel ; rappelle que `1.9.0` reste la seule release Production valide pour ce lot |
| Product Owner | Décision déjà actée (K5, 2026-07-05) : **release unique `1.9.0` après Sprint 12**. Arbitrage requis uniquement sur la question nouvelle §4.3/4.4 (sort de l'upgrade Angular 22 en attendant) |

## 7. Décision finale

**Chief Delivery Officer : NO GO — bloqué par construction (ADR-15 K5), aucune anomalie
technique.**

- `PRODUCTION_READY` : **non atteint.**
- `PRODUCTION_DEPLOYED` : non atteint.
- **Aucun déploiement de ce périmètre en Production n'est autorisé par ce document.**

### Condition bloquante avant toute nouvelle instruction de ce Gate

1. **RSV-PROD-EP14-01** — livraison complète du Sprint 12 (EP-14b), puis instruction d'un Gate
   Production unique `1.9.0` couvrant Sprint 11 + Sprint 12 + Angular 22.

### Point ouvert pour le Product Owner (ne bloque pas la trajectoire par défaut)

2. **RSV-PROD-EP14-02** — confirmer que l'option **A** (attendre le Sprint 12) reste retenue, ou
   demander le cadrage de l'option **C** (cherry-pick exceptionnel Angular 22 seul) si l'upgrade
   frontend est jugée urgente indépendamment des quittances certifiées.

### Prochaines étapes

| Étape | Document à produire |
|---|---|
| Cadrage Sprint 12 (EP-14b) | Plan déjà approuvé — `plan-execution-ep14-quittances-certifiees.md` (PR #182) |
| Exécution Sprint 12 | Rapport d'exécution dédié |
| Gate Staging combiné Sprint 11+12 | `gate-staging-*-v1.9.0-decision.md` (à créer) |
| Gate Production unique `1.9.0` | `gate-production-v1.9.0-decision.md` (à créer, remplace ce document) |
