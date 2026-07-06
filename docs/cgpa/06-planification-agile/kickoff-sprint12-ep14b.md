# Kickoff Sprint 12 — EP-14b : Vérification publique + observabilité

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Statut | **Cadrage de démarrage — en attente de GO PO explicite, aucun code produit** |
| Plan déjà approuvé | `plan-execution-ep14-quittances-certifiees.md` §Sprint 12 (PR #182, 2026-07-05) |
| Backlog déjà validé | `addendum-backlog-ep14.md` — US-102, US-103, US-104 |
| Prérequis | Sprint 11 (EP-14a) : mergé (`eddc037`), `STAGING_DEPLOYED` (`gate-staging-sprint11-ep14a-v5.4.1-decision.md`), Gate Production instruit **NO GO par construction** (`gate-production-sprint11-ep14a-decision.md` — attendu, ne bloque pas ce kickoff) |
| Release cible | `1.9.0` — Gate Production **unique**, après ce sprint (ADR-15 K5) |

## 1. Objet de ce document

Le Sprint 12 a déjà un plan d'exécution et un backlog approuvés en détail (GWT complet, §42-51 du
plan, §65-105 de l'addendum). Ce document ne re-cadre pas le sprint depuis zéro : il **confirme
la validité du plan** en le confrontant au code réellement livré par le Sprint 11 (le plan a été
écrit *avant* l'implémentation), et **isole les points d'implémentation concrets** qui n'étaient
pas visibles au moment du cadrage initial. C'est le point de contrôle GO/NO GO de démarrage,
distinct du GO global déjà donné sur le plan complet — même pattern que le kickoff Sprint 1
Patrimoine (2026-06-21) ou Sprint 3 Patrimoine (2026-06-25).

## 2. Vérification — ce que Sprint 11 a réellement préparé pour ce sprint

Le plan annonçait des fonctions `SECURITY DEFINER` « préparant le Sprint 12 ». Vérifié dans
`V22__ep14_quittance_certifiee.sql`, elles existent exactement comme prévu et sont déjà
`GRANT EXECUTE`-ées à `loyertracker_api` :

| Fonction | Rôle | Utilisable telle quelle par Sprint 12 ? |
|---|---|---|
| `lire_quittance_publique(uuid)` | Retourne `id, numero, version, statut, contenu, content_hash, pdf_hash, emise_le, token_kid, remplacante_numero, remplacante_version` | ✅ Oui — mais `contenu` est le **payload canonique complet** (voir §3.1, point d'attention) |
| `lire_pdf_quittance_publique(uuid)` | Retourne `pdf, pdf_hash, numero, version` pour le téléchargement | ✅ Oui, contrat déjà minimal |
| `journaliser_evenement_quittance(uuid, varchar, varchar)` | Insère `quittance_verification_log` + incrémente `nb_verifications`/`nb_telechargements` si `resultat='VALIDE'` | ✅ Oui, couvre US-104 sans modification |

`TokenQuittanceService.verifier(String token, UUID quittanceId, int version, String contentHash)`
existe déjà et est directement appelable par le futur contrôleur public — aucune extension de ce
service n'est nécessaire pour US-102.

Le format du QR déjà émis par le Sprint 11 (vérifié dans le rendu PDF Staging, Gate du 2026-07-06)
est bien `https://loyertracker.staging.loyerpro.org/verify/receipt/{id}?token=...&v=1` — conforme
exactement au contrat US-100/US-102, aucun écart entre ce qui est déjà imprimé et ce que Sprint 12
doit servir.

## 3. Points d'implémentation identifiés (raffinent le plan, ne le remettent pas en cause)

### 3.1 Risque de fuite via `contenu` (US-102 « aucune donnée hors contrat K2 ») — à traiter explicitement

`lire_quittance_publique` retourne `contenu`, le JSON canonique complet produit par
`ContenuQuittance` — **qui inclut `paiement.mode` et `garantie_retenue`**, deux champs que
US-102 interdit explicitement en public (« jamais... mode de paiement détaillé... »). Le futur
contrôleur public **ne doit pas** renvoyer `contenu` tel quel : il doit désérialiser puis
reconstruire une réponse strictement scopée K2 (numéro, bailleur {nom, adresse}, patrimoine {nom},
bien {adresse}, locataire {nom}, période, montants+devise, date d'émission, statut, version, hash).
**Action concrète pour Sprint 12** : le test de non-fuite déjà prévu au plan (« aucune donnée hors
contrat K2 ») doit explicitement inclure une assertion négative sur `paiement`/`mode` et
`garantie_retenue` absents de la réponse JSON publique — pas seulement une vérification positive
des champs attendus.

### 3.2 Rate-limit Nginx — infrastructure à créer, pas à étendre

Vérifié : `infra/nginx/nginx.conf` ne définit aujourd'hui **aucune** zone `limit_req`/`limit_conn`.
Le rate-limit sur `/api/public/` et `/verify/` (US-104) est donc un **ajout net**, pas une extension
d'un mécanisme existant — légèrement plus de travail que ne le laissait supposer une lecture rapide
du plan, mais sans risque architectural (`limit_req_zone` au niveau `http {}`, `limit_req` dans les
blocs `location` concernés, réplication à l'identique dans l'overlay `docker-compose.staging.yml`
et futur prod, cf. rappel déjà présent au plan sur l'invocation Compose canonique par environnement).

### 3.3 `SecurityConfig` — pattern déjà établi, aucune surprise

Le pattern `permitAll` ciblé existe déjà (`POST /api/invitations/*/acceptation`). Les deux
nouvelles routes publiques (`GET /api/public/receipts/{id}` et `.../download`) suivront le même
pattern, sans toucher aux routes existantes déjà gardées.

## 4. Prérequis et dépendances — statut

| Prérequis | Statut |
|---|---|
| Sprint 11 fusionné sur `main` | ✅ `eddc037` |
| K2 (RGPD, champs publics) tranché | ✅ ADR-15, 2026-07-05 |
| Fonctions `SECURITY DEFINER` disponibles | ✅ vérifiées §2 |
| Token HMAC vérifiable côté service | ✅ `TokenQuittanceService.verifier(...)` déjà présent |
| Gate Staging Sprint 11 | ✅ GO, `STAGING_DEPLOYED` (2026-07-06) |
| Gate Production Sprint 11 seul | NO GO **attendu et sans impact** — ADR-15 K5 prévoyait déjà que Sprint 11 seul ne serait jamais promu en Production |

Aucun prérequis bloquant résiduel.

## 5. Rappel du périmètre (inchangé depuis le plan approuvé)

- **US-102** — API publique `GET /api/public/receipts/{id}` + `/download`, contrat K2 strict,
  réponses indifférenciées (id inconnu / token invalide / version décalée / PDF altéré → même
  erreur, aucun oracle), compteurs.
- **US-103** — page Angular publique `/verify/receipt/:id` (route sans `authGuard`, `noindex`),
  ✓ Authentique / ❌ Invalide, statut temps réel (mention « remplacée par QT-… » le cas échéant),
  bouton téléchargement.
- **US-104** — métriques Prometheus (`quittance_verifications_total{resultat}`,
  `quittance_telechargements_total`, `quittance_qr_invalides_total`), journal RGPD-minimal (déjà
  couvert par `journaliser_evenement_quittance`, §2), rate-limit Nginx (§3.2).
- **Revue de sécurité dédiée exigée avant Gate Staging** (surface publique nouvelle) — déjà prévue
  au plan, rappelée ici comme critère GO non négociable compte tenu du point §3.1.

**Hors périmètre, inchangé** : toute UI/API de gestion des quittances au-delà de la vérification
publique ; toute modification du Sprint 11 déjà clos.

## 6. Critères GO de fin de sprint (repris du plan, inchangés)

✅ tests sécurité 100 % verts (incluant l'assertion négative §3.1) ✅ aucune donnée hors contrat K2
dans les réponses publiques ✅ smoke 59+N/0 avec compteur Flyway 22 (aucune nouvelle migration
prévue pour ce sprint — à confirmer en fin de sprint si un besoin de schéma apparaît) ✅ CI complète
verte ✅ Gate Staging PASS (dont `STG-ISOL-01` et rate-limit vérifié dans les deux overlays) →
**Gate Production `1.9.0`** (décision distincte, regroupant Sprint 11 + Sprint 12).

## 7. Décision

**Aucun code ne doit être produit avant GO explicite du PO sur ce kickoff**, conformément à
CLAUDE.md. Le plan et le backlog sont déjà approuvés dans le détail (§Sprint 12 du plan, US-102 à
104) ; ce document confirme leur validité face au code réel et ajoute trois précisions
d'implémentation (§3) sans changer le périmètre ni les critères GO déjà actés.
