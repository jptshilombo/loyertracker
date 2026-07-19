# Gate Production — Sprint C EP-15 (Bascule Bail → Locataire, US-113/114)

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| Version | `1.12.0` MINOR (proposée — à confirmer/dater au Préflight) |
| Candidat | `359f4d63…` (merge PR #229) / tag `sha-359f4d63` |
| Digest API | `sha256:ea040492bb5ad6b6a72b84665e22cd47a66d79c293b874fca481d5a276afe1c8` |
| Digest Web | `sha256:e70ebc7ba7d71406edaec6f890c2f57f06ae9d7c855680e0fba01914b4251968` |
| Source | `ai-test-server`, `STAGING_DEPLOYED` (`gate-staging-sprint-c-ep15-decision.md`) |
| Production / rollback | **`1.11.0`, `sha-cba13f52`** (base corrigée le 2026-07-19 — le déploiement `1.11.0` était réel mais non tracé au moment des faits ; régularisé, cf. `deploiement-technique-v1.11.0-report.md`/`prod-state.md` §0L) |
| Décision | **GO — `PRODUCTION_READY`** |

## Périmètre

Sprint C EP-15 (US-113/US-114, ADR-16 D2/D3/D6, Plan d'Exécution approuvé) : bascule du domaine
`Bail` vers un `Locataire` structuré. Migration **V26 non additive** (backfill 1 `Locataire`/bail
historique, `bail.locataire_id NOT NULL`, suppression de `bail.locataire_nom`/`locataire_email`).
Rupture de contrat HTTP intentionnelle en écriture (`POST .../baux` exige désormais
`locataireId`), lecture inchangée (`BailDto` dérivé du `Locataire` lié). Nouveau
`GET /api/biens/{bienId}/locataires` (lecture seule, Bailleur et Gestionnaire affecté). Effacement
RGPD retargeté sur `Locataire` (`DELETE /api/locataires/{locataireId}/effacement`, US-114).
Frontend : sélecteur de locataire + création rapide sur les deux tableaux de bord.

**Hors périmètre** : aucun changement des modules Garantie/Paiements ; aucune modification du
RBAC/ReBAC au-delà du nouvel endpoint de lecture scopé `@authz.peutAccederBien` ; le Sprint C
EP-15 n'a aucune dépendance technique avec EP-13 (déjà en Production), au-delà de la
renumérotation V25→V26 déjà tracée (`cadrage-sprint-c-ep15.md` §6).

## Checklist CGPA v5.4.1

| Critère | Statut | Preuve |
|---|---|---|
| Identification complète | PASS | Sprint C EP-15, `1.12.0` proposée, tag/digests, Staging vers Production |
| Candidat exact en Staging | PASS | `sha-359f4d63`, Gate Staging GO (`gate-staging-sprint-c-ep15-decision.md`) |
| `STG-ISOL-01` | PASS | 9 conteneurs `loyertracker-staging-*` + NPM mutualisé intacts avant/après, restart=0, aucune commande Docker globale |
| Migration / backfill Staging | PASS | Flyway V26 appliquée 26/26 ; backfill vérifié sur données réelles : 35/35 baux avec `locataire_id`, 0 orphelin |
| Smoke Staging | PASS | 63 PASS / 0 FAIL au premier passage (script étendu `locataireId` + endpoint RGPD `.../locataires/{id}/effacement`) |
| Vérification fonctionnelle dédiée | PASS | Vérification manuelle navigateur locale (avant Gate Staging) **et** vérification manuelle navigateur du PO contre `https://loyertracker.staging.loyerpro.org` (Bailleur : bien/locataire rapide/bail ; Gestionnaire : menu locataire peuplé) — `gate-staging-sprint-c-ep15-decision.md` |
| Sécurité | PASS — surface étendue mais contenue | Nouveau `GET /api/biens/{bienId}/locataires` gardé par `@authz.peutAccederBien` (même garde que les endpoints Bien existants), lecture seule, aucune nouvelle fonction `SECURITY DEFINER` ; RBAC/ReBAC bailleur/gestionnaire inchangés par ailleurs |
| CI / tests | PASS | CI GitHub 7/7 SUCCESS sur `359f4d6` (Backend+SonarQube, Frontend, Sécurité, Packaging Docker, CodeQL ×3) ; `mvn verify` 187/187 ; `ng build`/`ng test` 94/94 |
| SonarQube | PASS | Quality Gate bloquant vert — correctif `java:S107` (constructeur `Bail` à 9 paramètres, `@SuppressWarnings`, même patron que `Quittance`) pré-validé contre l'instance SonarQube réelle avant re-push |
| Migration / rollback | **PASS sous condition** | V26 **non additive** (suppression `locataire_nom`/`locataire_email`) — rollback applicatif vers `1.11.0` (`sha-cba13f52`) **non viable** une fois la migration appliquée (RSV-EP15-03, déjà tranchée par le PO, ADR-16 D3, même profil que V20/Sprint 9) ; seule la restauration du backup vérifié permet un retour arrière complet |
| Secrets Production | PASS — sans objet | Aucune nouvelle variable d'environnement/secret introduite (même contrat que Staging, aucun changement Compose/`.env.example`) |
| Observabilité | PASS | Aucun nouveau dispositif requis ; dispositif Production existant inchangé |
| PO / Release Manager | PASS | Plan d'Exécution EP-15 Sprint C respecté ; candidat, digests et rollback identifiés ; base de rollback corrigée (`1.11.0`, pas `1.10.0`) |
| Release notes / changelog | PASS sous condition | `CHANGELOG.md` `[Non publié]` couvre déjà le Sprint C EP-15 ; promotion en `[1.12.0]` datée à faire au Préflight |

## Rollback

**Migration V26 non additive** : rollback applicatif seul **non viable** après bascule
(`bail.locataire_nom`/`locataire_email` supprimées). Rollback vers `sha-cba13f52` (`1.11.0`)
possible uniquement **avant** l'application de V26 (images encore présentes/tirables, aucun
changement Compose). Après application de V26, seule la **restauration du backup vérifié** au
Préflight permet un retour arrière complet — même discipline que V20 (Sprint 9) et V24/EP-15
Sprints A+B avant lui (RSV-EP15-03).

## Réserves et conditions

| ID | Statut | Traitement |
|---|---|---|
| RSV-EP15-03 — migration V26 non additive, rollback applicatif non viable | **Acceptée par le PO** (ADR-16 D3) | Condition du Gate : Préflight renforcé exigeant un **backup disponible avant ET après** la migration V26, avant toute validation finale |
| RSV-EP15-01 — portée globale du statut Gestionnaire (héritée, sans rapport direct avec Sprint C) | Acceptée par le PO le 2026-07-08 | Surveillance continue, aucune action requise pour ce Gate |
| RSV-EP15-02 — backfill V26 ne peut pas séparer nom/prénom (`prenom` non peuplé) | Acceptée (ADR-16 D3) | Confirmé sur données réelles au Gate Staging (35/35, échantillon vérifié) ; correction manuelle possible hors périmètre bloquant |
| RSV-EP15-04 — asymétrie `BienService.archiver()` (dette technique, hors périmètre) | Ouverte, sans rapport avec ce Gate | Aucune action requise pour ce Gate |
| `RSV-STG-01` (héritée) | Maintenue, sans rapport avec ce périmètre | Inchangée |
| Écart de traçabilité `1.11.0` (déploiement réel non documenté à l'époque) | **Fermé le 2026-07-19** avant ce Gate | Régularisé a posteriori (`deploiement-technique-v1.11.0-report.md`/`validation-finale-v1.11.0-report.md`) ; base de rollback de ce Gate corrigée en conséquence |

Conditions du **Préflight distinct**, avant toute autorisation de déploiement :

1. vérifier Production `1.11.0` (`sha-cba13f52`), capacité, Flyway 25/25 et observabilité ;
2. vérifier backup base + globals **avant** migration (dump, catalogue, SHA-256, permissions) ;
3. confirmer les digests candidat (`sha256:ea040492…`/`sha256:e70ebc7b…`) et la disponibilité du rollback `sha-cba13f52` **avant** application de V26 ;
4. promouvoir le changelog en `[1.12.0]` daté et figer la date de go-live ;
5. **exiger explicitement, dans le rapport de déploiement technique, un second backup vérifié immédiatement après l'application de V26** — condition bloquante avant toute validation finale (RSV-EP15-03) ;
6. préparer un smoke ≥63 (script déjà étendu au Gate Staging) ;
7. confirmer le rollback ciblé API/Nginx (`api`+`nginx` seuls recréés, `postgres`/`keycloak`/monitoring inchangés) ;
8. rédiger le rapport de déploiement technique et la validation finale **dans la même session** que la bascule du tag `.env` — leçon de l'écart `1.11.0` régularisé ce même jour.

## Avis et décision

| Rôle | Avis |
|---|---|
| Governance Officer | **GO** — checklist et `STG-ISOL-01` PASS, historique de décisions et risques préservé ; base de rollback corrigée suite à la régularisation `1.11.0` du même jour |
| Enterprise Architect | **GO** — `BailDto` en lecture inchangé (dérivé de `Locataire`), seule l'écriture casse le contrat ; nouvel endpoint `GET /api/biens/{bienId}/locataires` limité en lecture, garde `@authz.peutAccederBien` déjà éprouvée |
| DevSecOps Lead | **GO** — CI/images/Staging/backfill/smoke tous PASS ; correctif Sonar pré-validé contre l'instance réelle avant re-push (leçon appliquée) ; Préflight renforcé obligatoire (backup avant **et** après V26) |
| Release Manager | **GO Staging → Production autorisée sous condition** ; candidat figé `sha-359f4d63` ; aucun déploiement inclus par ce document ; rollback applicatif borné à l'avant-migration |
| Product Owner | Plan d'Exécution Sprint C EP-15 respecté (US-113/114, périmètre inchangé) |
| Chief Delivery Officer | **GO — `PRODUCTION_READY`** |

**Décision finale : GO.** `PRODUCTION_READY` est atteint le 2026-07-19.

Cette décision autorise uniquement le **Préflight Production**. Elle n'autorise aucune mutation
ni aucun déploiement. Une instruction explicite distincte est requise après un Préflight PASS.
`PRODUCTION_DEPLOYED` reste non atteint.
