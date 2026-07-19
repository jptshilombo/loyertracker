# Plan d’Exécution CGPA (approuvé) — EP-16 : Notifications multicanales via Twilio

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| Statut | **Approuvé — GO explicite du PO reçu le 2026-07-19.** Sprint N (Fondation) autorisé à démarrer. Sprints N+1/N+2 restent soumis chacun à un GO distinct, après clôture Gate Staging/Production du sprint précédent |
| Origine | Instruction PO du 2026-07-19 (« formaliser EP-16 notifications multicanales Twilio ») |
| Backlog couvert | EP-16 — US-119 → US-126 (`addendum-backlog-ep16-notifications.md`) |
| ADR | **ADR-18** (Acceptée — kickoff K1→K8 clos et GO Plan reçu, 2026-07-19) |
| Release cible | À déterminer pour les Sprints N+1/N+2 ; Sprint N fusionné mais non déployé |
| État d’exécution | K1→K8 tranchés et GO explicite reçu le 2026-07-19 ; Sprint N codé puis fusionné via PR #235 (CI complète et Quality Gate SonarQube verts). Gate Staging du Sprint N, dont `STG-ISOL-01`, restant à instruire ; Sprints N+1/N+2 non autorisés à démarrer sans leur GO distinct |

## Arbitrages PO — K1→K8 tranchés le 2026-07-19

| # | Question | Décision PO (2026-07-19) | Statut |
|---|----------|--------------------------|--------|
| K1 | Destinataires par événement | P0 : locataire pour quittance/garantie/retard ; bailleur et gestionnaire pour suivi opérationnel, selon autorisations et préférences (recommandation adoptée) | ✅ Tranché |
| K2 | Canal principal | IN_APP obligatoire, WHATSAPP principal, SMS secours (recommandation adoptée) | ✅ Tranché |
| K3 | Mode de recueil du consentement | Formulaire natif LoyerTracker, opt-in explicite (aucune recommandation par défaut n'existait — décision propre du PO) | ✅ Tranché |
| K4 | Stratégie de numéro | Réutiliser un numéro existant (aucune recommandation par défaut n'existait — décision propre du PO) | ✅ Tranché |
| K5 | Fallback SMS | Pas de fallback automatique au premier pilote (recommandation adoptée) | ✅ Tranché |
| K6 | Historique visible | Bailleur : tout son périmètre ; gestionnaire : son propre périmètre affecté uniquement (aucune recommandation par défaut n'existait — décision propre du PO) | ✅ Tranché |
| K7 | Rétention des métadonnées de livraison | Alignement strict sur l'audit métier existant, sans durée fixe distincte (recommandation partielle confirmée) | ✅ Tranché |
| K8 | Stratégie de release | Déployer le socle désactivé, valider Staging, puis activer progressivement après validation complète du P0 (recommandation adoptée) | ✅ Tranché |

**Le kickoff K1→K8 est clos** (détail des décisions et alternatives écartées :
`ADR-18-notifications-multicanales-twilio.md` §Décisions). Quatre points disposaient d’une
recommandation par défaut (K1, K2, K5, K8, toutes adoptées sans modification) ; quatre n’en avaient
aucune (K3, K4, K6, K7), tranchés directement par le PO. Le GO explicite reçu ensuite sur ce Plan
a autorisé uniquement le Sprint N, désormais fusionné via PR #235 ; il **n’autorise toujours ni le
Sprint N+1, ni le Sprint N+2, ni aucun déploiement**.

## Vue d'ensemble

Contrairement à EP-13 (lot additif, sprint unique), EP-16 introduit un **nouveau domaine
transverse** (notifications), un **nouveau service externe critique** (Twilio) et un **nouveau
pattern technique** (Outbox transactionnelle avec traitement concurrent) — un découpage en
plusieurs sprints est nécessaire, avec un principe strict : **aucun sprint capable d'émettre un
message réel n'est promu en Production tant que consentement, idempotence, callbacks, plafond
budgétaire, observabilité et kill switch ne sont pas opérationnels** (recommandation de la mission,
reprise ici comme condition de Gate).

```
Sprint N — Fondation (aucun envoi réel possible)
        │  US-119, US-120, US-121
        ▼
Sprint N+1 — WhatsApp P0 (Twilio Sandbox uniquement, jamais Production tant que non validé)
        │  US-122, US-123
        ▼
Sprint N+2 — SMS fallback et UX
        │  US-124, US-125, US-126 (socle observabilité amorcé dès Sprint N)
```

Trois sprints sont recommandés plutôt que deux ou un seul release unique : le pattern Outbox
concurrent (RSV-EP16-01/02) et le consentement (RSV-EP16-04) doivent être **prouvés en Staging
avant** d'introduire un fournisseur réel, même en mode Sandbox — un lot unique combinant fondation
et envoi réel démultiplierait le risque de régression sur un mécanisme entièrement nouveau pour ce
projet.

## Sprint N — Fondation

| Champ | Valeur |
|---|---|
| Objectif | Modèle de données, Outbox transactionnelle, abstraction fournisseur — **aucun envoi réel possible**, implémentation fournisseur en sandbox/fausse implémentation uniquement |
| Stories | US-119 (préférences/consentement), US-120 (modèle Notification + Outbox), US-121 (abstraction fournisseur) |
| Livrables | Migration additive **V27** (numéro à reconfirmer, sous réserve d'absence de collision au démarrage réel) : `notification_preference`, `notification_event`, `notification_outbox`, `notification_delivery`, `notification_template` (RLS `bailleur_isolation` sur les quatre premières, référentiel global sans RLS sur la dernière) ; extension de `generer_alertes()` (voie A) ; écriture inline dans `QuittanceCertifieeService`/`GarantieService`/`PaiementService`/`BailService` (voie B) ; interface `NotificationProvider` + implémentation `NoopNotificationProvider`/sandbox ; feature flags `NOTIFICATIONS_EXTERNAL_ENABLED=false` etc. ; tests unitaires + intégration RLS/idempotence |
| Hors périmètre | Tout appel réseau Twilio réel ; toute création de compte/credentials Twilio ; tout envoi WhatsApp/SMS |
| Dépendances | K1/K3 tranchés et GO explicite reçus le 2026-07-19 ; code fusionné via PR #235. Gate Staging du Sprint N, dont `STG-ISOL-01`, restant à instruire |
| Risques | RSV-EP16-01/02 couverts côté code par les tests dédiés de rollback, idempotence et concurrence ; preuve Staging restant requise |
| Critères GO (fin de sprint) | ✅ `mvn verify`, RLS cross-tenant, concurrence Outbox, idempotence, rollback métier, démarrage sans Twilio, non-régression in-app et CI complète ; ⏳ Gate Staging, dont `STG-ISOL-01`, restant à instruire |

## Sprint N+1 — WhatsApp P0

| Champ | Valeur |
|---|---|
| Objectif | Premiers envois WhatsApp réels, en environnement **Twilio Sandbox** exclusivement (numéro de test Twilio, jamais un compte Production tant que non validé) |
| Stories | US-122 (WhatsApp transactionnel P0 : `QUITTANCE_DISPONIBLE`, `LOYER_EN_RETARD`, `GARANTIE_DEBITEE`), US-123 (callbacks, suivi, retries) |
| Livrables | `TwilioNotificationProvider` (implémentation réelle, credentials Sandbox) ; templates P0 formalisés côté `NotificationTemplate` (soumission réelle à l'approbation Twilio hors périmètre de cette mission, mais le mécanisme de statut d'approbation doit être opérationnel) ; endpoint callback public + vérification de signature ; `NotificationDispatcher` opérationnel ; tests d'intégration dédiés (callback valide/invalide/dupliqué, dead-letter) |
| Hors périmètre | Tout envoi vers un numéro Twilio de Production ; tout fallback SMS (US-124, sprint suivant) |
| Dépendances | Sprint N clos en GO ; K3/K4 tranchés (2026-07-19) |
| Risques | RSV-EP16-04, RSV-EP16-06 |
| Critères GO (fin de sprint) | ✅ Callback signature invalide rejeté sans effet de bord (test dédié) ✅ callback dupliqué sans transition supplémentaire ✅ template non approuvé ⇒ `DEAD`, aucun envoi ✅ vérification manuelle en Sandbox du parcours complet (quittance disponible → WhatsApp reçu → lien de vérification valide) ✅ CI complète verte ✅ Gate Staging |

## Sprint N+2 — SMS fallback et UX

| Champ | Valeur |
|---|---|
| Objectif | Fallback SMS maîtrisé, interface de préférences/historique, observabilité et exploitation complètes — condition préalable à toute activation Production réelle |
| Stories | US-124 (SMS fallback contrôlé), US-125 (interface préférences/historique), US-126 (observabilité/sécurité/exploitation) |
| Livrables | Politique de fallback (feature flag dédié) ; UI préférences/historique (nouveau, aucun écran existant à étendre — cf. analyse d'impact §6) ; métriques `notification.*` + alertes Alertmanager `component: notifications` ; extension additive de `observability-governance.md` ; runbook d'exploitation (incident Twilio, reprise manuelle, rotation secrets) ; plafond budgétaire opérationnel |
| Hors périmètre | Auto-gestion des préférences par le `Locataire` lui-même (aucun compte applicatif existant — hors périmètre P0, cf. K1/K4) |
| Dépendances | Sprint N+1 clos en GO ; K5/K6/K7/K8 tranchés (2026-07-19) |
| Risques | RSV-EP16-03, RSV-EP16-05 |
| Critères GO (fin de sprint) | ✅ Fallback SMS jamais déclenché sans opt-in + politique explicite (test dédié) ✅ plafond budgétaire testé (dépassement simulé ⇒ arrêt/limitation) ✅ isolation cross-tenant de l'historique (test dédié) ✅ `observability-governance.md` étendu ✅ runbook rédigé et revu ✅ CI complète verte ✅ Gate Staging → **Gate Production distinct, sous condition K8** |

## Stratégie Twilio Sandbox

Aucune création de compte Twilio n'est autorisée par cette mission (interdiction stricte, §19 du
prompt de mission). Au démarrage du Sprint N+1, le PO ou l'exploitant devra provisionner un compte
Twilio (Sandbox WhatsApp inclus) **hors du périmètre de ce Plan** — la stratégie recommandée est :
Sandbox Twilio pour Dev/Staging (numéro de test, opt-in manuel par code), compte Production distinct
provisionné uniquement après GO explicite sur le Sprint N+2 clos (K8).

## Stratégie de secrets

`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` (ou `TWILIO_API_KEY`/`TWILIO_API_SECRET` si une clé
restreinte est disponible pour le mode Twilio retenu), `TWILIO_WHATSAPP_FROM`, `TWILIO_SMS_FROM`,
`TWILIO_STATUS_CALLBACK_BASE_URL` — secrets distincts par environnement (dev/staging/prod), jamais
versionnés, convention `${VAR}`/`${VAR:}` déjà en place. Dès le Sprint N, `.env.example` documente
uniquement les quatre feature flags non secrets et leurs valeurs sûres par défaut. Les variables
de credentials Twilio ne seront ajoutées avec des placeholders `CHANGE_ME` qu’au Sprint N+1,
au moment de l’intégration réelle du fournisseur ; aucune valeur réelle ne sera versionnée.

## Stratégie Staging

Gate Staging distinct pour chaque sprint (dont `STG-ISOL-01` sur l'hôte mutualisé
`ai-test-server`), comme pour tout lot de ce projet. Le Sprint N+1 est le premier à nécessiter un
secret Twilio réel (Sandbox) sur l'hôte Staging mutualisé — à traiter avec la même discipline que
les secrets Keycloak/GHCR déjà en place (jamais en clair dans les logs, leçon de l'incident
`set -x` du Gate Staging EP-13).

## Stratégie Production

Aucune activation Production tant que le Sprint N+2 n'est pas clos en GO (recommandation de
principe, K8). Le Gate Production du Sprint N+2 devra explicitement vérifier : plafond budgétaire
opérationnel, kill switch testé, observabilité étendue, aucun secret Twilio de Sandbox réutilisé en
Production.

## Feature flags (rappel, cf. ADR-18)

```text
NOTIFICATIONS_EXTERNAL_ENABLED=false
TWILIO_WHATSAPP_ENABLED=false
TWILIO_SMS_ENABLED=false
NOTIFICATION_DRY_RUN=true
```

Tous à `false`/`true` (DRY_RUN) par défaut à chaque environnement tant qu'une activation explicite
n'est pas décidée par le PO, sprint par sprint.

## Tests par sprint (socle Sprint N codé ; intégrations Twilio N+1/N+2 planifiées)

- **Unitaires** : transitions d'état Outbox/Delivery, idempotence, classification des erreurs
  (temporaire/permanente), politique de retry, fallback, consentement, rendu des variables de
  template, masquage des données.
- **Intégration** : Outbox dans la même transaction que le métier ; rollback métier ⇒ aucune
  Outbox persistée ; succès métier + Twilio indisponible ⇒ Outbox en attente ; callback valide ;
  callback invalide ; callback dupliqué ; isolation RLS cross-tenant ; dead-letter ; plafond
  budgétaire ; kill switch.
- **Non-régression** : alertes in-app existantes (`AlerteService`, `generer_alertes()`), batch
  quotidien, paiements, garanties, quittances certifiées (QR/téléchargement), RGPD, gestionnaires,
  locataires, audit, métriques, `infra/smoke/smoke-stack.sh` (compteur Flyway à réaligner).
- **Sécurité** : secret absent du dépôt (Gitleaks CI déjà en place), signature callback, absence de
  PII dans les logs, absence de téléphone dans les labels Prometheus, absence d'accès cross-tenant,
  validation des liens, rate limiting Nginx, injection dans les variables de template.

## Gouvernance transverse

| Artefact | Échéance |
|---|---|
| ADR-18 acceptée (K1→K8 tranchés) | ✅ 2026-07-19 |
| Addendum EB (BF-106→111) | ✅ Produit avec ce plan |
| Addendum CDC (EF-113→124, RM-114→123, ENF-94→97) | ✅ Produit avec ce plan |
| Addendum backlog EP-16 (US-119→126) | ✅ Produit avec ce plan |
| Analyse d'impact EP-16 | ✅ Produite avec ce plan |
| Kickoff K1→K8 tranché par le PO | ✅ 2026-07-19 |
| `CHANGELOG.md` `[Non publié]` au fil de chaque sprint | À la fusion `main` |
| `docs/project-state.md` / `staging-state.md` / `prod-state.md` | Chaque Gate |
| `docs/cgpa/observability-governance.md` (extension additive) | Avant Gate Production du Sprint N+2 |

## Checklist de validation CGPA (avant tout codage)

- [x] `docs/project-state.md`/`AGENTS.md`/`CLAUDE.md`/`docs/cgpa/README.md` lus, phase courante
  identifiée (CGPA v5.4.1, release `1.12.0` `PRODUCTION_DEPLOYED` le 2026-07-19, hypercare T0
  PASS en cours)
- [x] Aucune décision, Gate ou risque historique supprimé ou réécrit
- [x] Numérotation vérifiée sans collision réelle — **collision détectée et corrigée** :
  US-115→118 déjà occupées (EP-13), repris à US-119→126 ; EP-16/ADR-18/D-NOTIF-001/EF-113+/
  RM-114+/ENF-94+ libres, vérifiés sans collision
- [x] Impact Staging/Production/Release Management analysé (aucun déploiement à ce stade)
- [x] Kickoff K1→K8 tranché par le PO (2026-07-19)
- [x] Plan d'Exécution approuvé (GO explicite du PO, 2026-07-19) — **Sprint N autorisé à démarrer**
- [ ] Sprint N instruit avec son propre Gate Staging (dont `STG-ISOL-01`) et sa propre décision
  Gate Production — Sprints N+1/N+2 idem, chacun distinctement

## Ce que ce plan autorise et n'autorise **pas**, à ce stade (Sprint N)

Le GO du 2026-07-19 autorise le **codage du Sprint N — Fondation** (US-119/120/121 : modèle de
données, migration additive **V27**, Outbox transactionnelle, abstraction `NotificationProvider`
+ `NoopNotificationProvider`/sandbox, feature flags à `false`, tests unitaires/intégration
RLS/idempotence), strictement dans les bornes déjà fixées par ce Plan (§Sprint N — Fondation) et
par ADR-18. Restent **exclus, y compris pendant le Sprint N** :

- Toute dépendance Twilio ajoutée à `pom.xml`/`package.json` (réservé au Sprint N+1).
- Toute création de compte, de credentials ou de template Twilio.
- Tout envoi SMS ou WhatsApp réel, tout appel réseau Twilio réel.
- Toute modification de Docker/infrastructure.
- Tout déploiement, toute release, tout statut `STAGING_READY`/`STAGING_DEPLOYED`/
  `PRODUCTION_READY`/`PRODUCTION_DEPLOYED` marqué par ce document — soumis au Gate Staging (dont
  `STG-ISOL-01`) puis à une décision Gate Production distincte, propres au Sprint N.
- Tout démarrage du Sprint N+1 ou N+2 sans un GO explicite du PO propre à chacun — ce GO ne couvre
  que le Sprint N (même principe que les Sprints A/B/C d'EP-15, chacun autorisé séparément).

## Estimation globale

| Sprint | Points | Estimation (ordre de grandeur, non contractuelle) |
|---|---|---|
| Sprint N — Fondation | 29 | Le plus dense techniquement (modèle de données + Outbox concurrent, pattern entièrement nouveau) |
| Sprint N+1 — WhatsApp P0 | 21 | Dépend de la disponibilité d'un compte Twilio Sandbox (hors périmètre) |
| Sprint N+2 — SMS fallback et UX | 21 | Inclut la première UI de gestion de préférences de ce projet (aucun écran existant à étendre) |
| **Total EP-16** | **71** | Cohérent avec le plus grand epic multi-sprint déjà livré par ce projet (EP-15, 3 sprints) |
