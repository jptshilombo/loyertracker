# Clôture Release `1.10.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-16 |
| Heure CDO GO | ~15:20 UTC (après checkpoint T+24 en rattrapage, PASS) |
| Release | `1.10.0` |
| Tag Production | `sha-c9200a51` |
| Commit applicatif | merge PR #209 (`4d8a760` — EP-15 Sprint A+B) suivi du merge PR #217 (doc, `c9200a51`) |
| `PRODUCTION_DEPLOYED` | 2026-07-15 ~13:41 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.10.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| EP-15 instruit (ADR-16, plan d'exécution, backlog US-105→114) | 2026-07-08 | Kickoff K1 tranché (« profil sur compte existant ») ; 3 sprints A/B/C | `ADR-16-gestion-personnes.md`, `plan-execution-ep15-personnes.md` |
| Sprint A (Gestionnaire, US-105→108) | 2026-07-08 | Codé et vert, `mvn verify` 168/168 | Migration V23 |
| Sprint B (Locataire, US-109→112) | 2026-07-09 | Codé et vert, `mvn verify` 173/173 | Migration V24 |
| PR #209 — revue de code + merge | 2026-07-15 | 2 bugs corrigés avant merge (photo Gestionnaire effacée sur mise à jour partielle, `dateCreation` Locataire nulle) ; CI 7/7 ; merge `4d8a760` | `main` |
| Gate Staging Sprints A+B EP-15 | 2026-07-15 | GO — `STAGING_DEPLOYED` (`sha-c9200a51`), `STG-ISOL-01` PASS, Flyway 24/24, smoke 62/0, vérification manuelle endpoints 48/0 | `gate-staging-sprints-ab-ep15-decision.md` |
| Gate Production Sprints A+B (`1.10.0`) | 2026-07-15 | GO — `PRODUCTION_READY` | `gate-production-v1.10.0-decision.md` |
| Préflight + backup | 2026-07-15 | PASS — `loyertracker-20260715-120812.dump` (749644 octets, SHA-256 `f4d5e2cb…` vérifié) | `preflight-backup-v1.10.0-report.md` |
| Déploiement technique | 2026-07-15 | PASS — V23+V24 appliquées 24/24, `api`+`nginx` seuls recréés, digests conformes | `deploiement-technique-v1.10.0-report.md` |
| Validation finale (smoke Production) | 2026-07-15 ~13:41 UTC | **62 PASS / 0 FAIL au premier passage**, nettoyage transactionnel sans résidu ; `PRODUCTION_DEPLOYED` | `validation-finale-v1.10.0-report.md` |
| Hypercare T0 | 2026-07-15 ~13:26 UTC | PASS | `plan-etape-hypercare-v1.10.0.md` |
| Hypercare T+12 (rattrapage) | 2026-07-16 ~10:57 UTC | PASS — hôte resté allumé en continu, `restart=0` | idem |
| Hypercare T+24 (rattrapage) | 2026-07-16 ~15:01 UTC | PASS — aucun critère de suspension atteint | idem |

## 2. Périmètre livré

**EP-15 — Gestion des personnes, Sprints A+B (Gestionnaire + Locataire, ADR-16)**

- Migration **V23** (additive) : statut global Gestionnaire (suspension/réactivation, archivage
  cross-tenant conditionné), fonctions `SECURITY DEFINER` `gestionnaire_a_affectation_active` /
  `gestionnaire_a_relation`.
- Migration **V24** (additive) : table `locataire` (RLS `ENABLE`+`FORCE`, policy
  `bailleur_isolation`, index dédiés) ; `bail.locataire_id` ajoutée nullable, sans aucun usage
  applicatif — préparation du Sprint C.
- **US-105→108** (Gestionnaire) : profil, suspension/réactivation immédiates, archivage
  cross-tenant conditionné, restauration, recherche, détection de doublons, historique ;
  activation Keycloak pilotée par l'application.
- **US-109→112** (Locataire) : `/api/locataires` — création, modification, archivage sans
  précondition, restauration, recherche, détection de doublons, historique ; cloisonnement
  entièrement porté par la RLS (le Locataire n'est pas un compte utilisateur).
- Le **Sprint C** (bascule `Bail → Locataire`, migration **V25 non additive**, US-113/114) reste
  explicitement hors périmètre de cette release — cadré NO-GO « pas encore » le 2026-07-16, dans
  l'attente précise de cette clôture.

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| RSV-EP15-01 | Statut Gestionnaire **global** (pas par bailleur) — risque cross-tenant | ✅ **Acceptée par le PO au cadrage** (ADR-16) ; non bloquante, aucun incident observé en Production |
| RSV-EP15-02→04 | Migration historique sans déduplication / suppression colonnes texte libre / photo `bytea` | ✅ Tranchées par le PO au cadrage, hors périmètre à la clôture |
| Écarts de fenêtre hypercare (T+12/T+24 en rattrapage) | Contrôle live hors créneau cible | ✅ **QUALIFIÉS sans impact** — hôte resté allumé en continu tout du long (`restart=0` sur les 8 conteneurs), à la différence du pattern `1.4.0`→`1.9.0` (hôte volontairement éteint) |
| Faux positif d'invariant ledger au checkpoint T+24 | Erreur de reconstruction de la requête de contrôle, pas un incident de production | ✅ **RÉSOLU** — vérifié contre le ledger réel (`garantie_movement`) et le code (`Garantie.retenirSurLoyer`) : comportement voulu, `montantRetenu` n'est mis à jour qu'à la restitution |
| Horodatage T+24 initialement erroné (heure locale WAT confondue avec UTC) | Erreur méthodologique de contrôle (`uptime` affiche l'heure locale de l'hôte, pas l'UTC) | ✅ **CORRIGÉE** dans `plan-etape-hypercare-v1.10.0.md` — leçon actée : toujours confirmer avec `date -u` |
| `RSV-STG-01` (héritée) | Confirmation live `STG-ISOL-01` au prochain déploiement Staging mutualisé | ⚠️ **MAINTENUE** — sans rapport avec `1.10.0` |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-c9200a51` |
| Digest API | `sha256:37de87e86dfe99d0483ef6ac1934384e773f858822ab53bbe29432e7d6858db9` |
| Digest Web (nginx) | `sha256:7ade9816f3844f10d2e8a9f63491380546d6c68370011d38fc04368ee5e51052` |
| Flyway | V1→**V24** (24/24) |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 depuis le déploiement (~27 h) |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | 0 alerte active |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| 5xx depuis le déploiement (~27 h) | 0 |
| Invariant ledger (garantie) | 8/8 PASS |
| Données métier | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 0 locataire (Sprint C non démarré), 6 quittances |
| `bailleur-test` | `enabled=false` ; `directAccessGrantsEnabled=false` (clients applicatifs) |
| Site public | `https://loyertracker.loyerpro.org` → 200 |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.10.0` CLÔTURÉE le 2026-07-16.**

- Hypercare complète : T0, T+12 (rattrapage) et T+24 (rattrapage) tous PASS, aucun critère de
  suspension atteint sur l'un des trois checkpoints.
- US-105→112 (Gestionnaire + Locataire, cycle de vie) confirmées stables en Production sur
  ~27 h : 0 restart, 0 dérive de tag/digest, 0 5xx, invariant du ledger garantie constant 8/8.
- Les deux écarts méthodologiques rencontrés pendant l'hypercare (faux positif d'invariant,
  horodatage UTC erroné) sont documentés et corrigés ; aucun des deux ne traduisait une anomalie
  de production réelle.
- RSV-EP15-01→04 restent non bloquantes (tranchées au cadrage ou hors périmètre). `RSV-STG-01`
  (héritée) maintenue, sans rapport avec `1.10.0`.
- Cette clôture lève la condition de démarrage du **Sprint C EP-15** (bascule `Bail → Locataire`,
  migration V25 non additive) posée par le Plan d'Exécution EP-15 (« Sprint B stable en
  Production depuis au moins un cycle de release complet, sans anomalie ») — un **GO explicite du
  PO** reste néanmoins requis avant tout codage.
- Cette clôture satisfait également le prérequis « release `1.10.0` clôturée (CDO GO) » posé par
  le Plan d'Exécution **EP-13 (Fin de bail)** — le kickoff K1→K6 d'EP-13 reste néanmoins
  entièrement à trancher par le PO avant tout GO sur ce lot.
- Prochaines actions autorisées : GO explicite du PO sur le Sprint C EP-15, et/ou tranchage du
  kickoff K1→K6 d'EP-13 — les deux chantiers restent indépendants et non exclusifs l'un de
  l'autre (RSV-EP13-04, coordination de séquencement Production à arbitrer si menés en
  parallèle).
