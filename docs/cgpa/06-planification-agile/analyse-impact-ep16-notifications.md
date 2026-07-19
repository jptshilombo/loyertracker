# Analyse d'Impact — EP-16 : Notifications multicanales via Twilio

| Champ | Valeur |
|-------|--------|
| Statut | **Documentation seule — aucun développement engagé.** Conforme à la règle CGPA « Codage suspendu : Plan d'Exécution requis avant modification du code ». |
| Date | 2026-07-19 |
| Demandeur | PO (jordan) |
| Niveau | **Niveau 3** (nouveau domaine transverse, nouvelles tables RLS, nouveau service externe critique, impacts sur alertes/paiements/garanties/quittances/frontend) |
| Documents liés | `ADR-18-notifications-multicanales-twilio.md`, `addendum-notifications-multicanales.md` (EB/CDC), `addendum-backlog-ep16-notifications.md`, `plan-execution-ep16-notifications.md` (à produire) |
| Contexte de gouvernance | Release `1.12.0` (Sprint C EP-15) est `PRODUCTION_DEPLOYED` depuis le 2026-07-19, hypercare en cours (checkpoint T0 PASS). Cette analyse est purement documentaire et n'ouvre aucun Sprint ; elle ne dépend pas de la clôture `1.12.0` mais aucun Sprint réel ne doit démarrer avant un GO explicite du PO — clôture de release et autorisation de nouveaux travaux ne doivent jamais être confondues (règle CGPA explicite). |

## 0. Méthode

Trois explorations factuelles du code ont précédé toute conclusion (aucune modification) :
1. Domaine backend — `alertes`, `audit`, `paiements`, `garanties`, `quittances`/`documents`,
   `locataires`/`comptes`, `securite`.
2. Schéma Flyway complet (V1→V26), pattern RLS, fonctions `SECURITY DEFINER`, contraintes
   d'unicité, rôles PostgreSQL.
3. Configuration (`application.yml`, Docker Compose, CI, observabilité) et frontend (alertes,
   dashboards, absence de gestion UI des locataires/gestionnaires).

Les constats ci-dessous citent des fichiers réels — ce sont des faits, pas des hypothèses.

---

## 1. Impact Domaine (backend)

**Nouveau package proposé** : `com.loyertracker.notifications` (préférences, événements, outbox,
delivery, templates, dispatcher, provider).

**Aucune modification des services métier existants requise au-delà d'un appel d'écriture
supplémentaire**, exactement analogue à l'appel `audit.enregistrer(...)` déjà présent :

| Service existant | Modification requise |
|---|---|
| `PaiementService.pointer(...)` | Un appel supplémentaire (voie transactionnelle, `PAIEMENT_RECU`, P1) dans la même transaction — aucun changement de signature, aucun couplage Twilio |
| `GarantieService.retenirSurLoyer(...)` | Idem (`GARANTIE_DEBITEE`, P0) |
| `QuittanceCertifieeService.emettre(...)` | Idem (`QUITTANCE_DISPONIBLE`, P0) |
| `AlerteService`/`generer_alertes()` (SQL) | Extension de la fonction SQL batch pour produire aussi `notification_event`/`notification_outbox` (`LOYER_EN_RETARD`, `FIN_BAIL`, `PREAVIS`, `GARANTIE_NON_RESTITUEE`, P0/P1) — aucune modification de `AlerteService` lui-même, ni de `AlertesScheduler` |
| `BailService.creer`/`cloturer` | Idem (`BAIL_CREE`/`BAIL_CLOS`, P1) |
| `Locataire`, `Gestionnaire`, `Bailleur` | Aucune modification de champ existant — `NotificationPreference` est une table séparée référençant ces entités par `recipient_id` polymorphe (sans FK stricte, patron `Alerte.destinataireId`) |

**Constat clé** : aucune alerte n'est aujourd'hui créée de façon synchrone dans une transaction
métier (`generer_alertes()` batch uniquement) — ceci impose la distinction en deux voies
d'alimentation de l'Outbox détaillée dans `ADR-18-notifications-multicanales-twilio.md` §2, plutôt
qu'un unique point d'entrée générique.

## 2. Impact Base de données

Cinq tables nouvelles (`notification_preference`, `notification_event`, `notification_outbox`,
`notification_delivery`, `notification_template`), toutes additives — **aucune modification** de
`alerte`, `bail`, `paiement`, `garantie`, `garantie_movement`, `quittance`, `locataire`,
`gestionnaire`, `bailleur`. Migration réservée : **V27** (dernière migration réelle à ce jour :
V26), à reconfirmer au Plan d'Exécution en cas de migration intercurrente.

Pattern à respecter strictement (déjà éprouvé, aucune invention) :
- `bailleur_isolation` (RLS `ENABLE`+`FORCE`) sur toutes les tables `bailleur_id`-scopées.
- `NotificationTemplate` en référentiel global sans RLS (patron `type_bien`).
- `NotificationDelivery` en ledger append-only (patron `garantie_movement`).
- Contrainte unique `(event_id, recipient_id, notification_type, channel)` sur
  `notification_outbox`.

**Point technique non couvert par un précédent exact** : le traitement multi-bailleur de l'Outbox
par un job `@Scheduled` nécessitera soit le rôle `loyertracker_batch` (BYPASSRLS) + fonctions
`SECURITY DEFINER` dédiées, soit une itération bailleur par bailleur — à trancher au Plan
d'Exécution (aucun job existant ne traite aujourd'hui une table de « travail en cours »
concurrent).

Deux tests de compteur Flyway à incrémenter en même temps que toute migration future
(`SchemaMigrationTest.java`, `infra/smoke/smoke-stack.sh`) — leçon déjà tracée dans ce projet
(incident R-S04-1/PR #77, compteur oublié après ajout de V14).

## 3. Impact Sécurité

- Nouvelle surface : endpoint callback public (`/api/public/notifications/twilio/status`),
  whitelisté comme `PublicQuittanceController`, vérification de signature `X-Twilio-Signature`
  obligatoire (jamais de Bearer JWT sur cet endpoint).
- Nouvelle route Nginx dédiée avec sa propre zone `limit_req_zone` (patron
  `public_quittances` déjà en place, `infra/nginx/nginx.conf`).
- Nouveaux secrets (`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`/`TWILIO_API_KEY`+`TWILIO_API_SECRET`,
  `TWILIO_WHATSAPP_FROM`, `TWILIO_SMS_FROM`, `TWILIO_STATUS_CALLBACK_BASE_URL`) — convention
  `${VAR}`/`${VAR:}` déjà en place, secrets distincts par environnement.
- Aucune extension du RBAC/ReBAC existant sur les modules Paiement/Garantie/Bail/Quittance —
  uniquement de nouvelles routes propres aux préférences/historique de notification, scopées RLS.
- CI : l'ajout du SDK Twilio (Java, et potentiellement JS si un widget de préférence en dépend)
  déclenchera automatiquement les scans SCA/Trivy déjà en place — aucune configuration CI
  supplémentaire nécessaire pour cette mission documentaire.

## 4. Impact RGPD

- Nouvelle catégorie de donnée personnelle (téléphone, historique de livraison, consentement) à
  intégrer au flux RGPD déjà en place : `RgpdService.exporter()` devra inclure
  `NotificationPreference`/`NotificationDelivery` du bailleur exportateur ;
  `RgpdService.anonymiserLocataire()` (EP-15) devra couvrir les préférences et l'historique du
  `Locataire` anonymisé.
- Minimisation stricte du payload transmis à Twilio (`payload_minimal` JSONB) — jamais de PII
  étendue, jamais de PDF base64 (réutilisation du lien HMAC EP-14).
- Durée de rétention des métadonnées de livraison non tranchée (K7) — impact direct sur une future
  tâche de purge, à l'image du modèle de rétention déjà pratiqué pour les sauvegardes (7 j
  quotidien / 4 semaines hebdomadaire).

## 5. Impact API

Quatre endpoints proposés (préférences GET/PUT, désinscription, historique, callback Twilio) —
voir `addendum-notifications-multicanales.md` (CDC) §5 pour le détail. Aucun contrat existant
modifié (`BailDto`, `PaiementDto`, `GarantieDto`, `QuittanceDto`, `AlerteDto` restent inchangés).

## 6. Impact UI (frontend)

- **Aucune page de gestion des locataires/gestionnaires n'existe aujourd'hui** — toute UI de
  préférences de notification est à construire depuis zéro, pas à greffer sur un écran existant.
- Le composant d'alertes in-app (`alertes-liste.component.ts`, partagé bailleur/gestionnaire) reste
  inchangé — aucune fusion avec l'historique de notification externe (patrons de lecture
  distincts, cf. ADR-18 §Modèle de données, justification Option B).
- Point ouvert non trivial (cf. EB addendum §4) : le `Locataire` n'a **aucun compte applicatif**
  aujourd'hui — la gestion de ses propres préférences nécessite une saisie par le bailleur pour
  son compte, ou un mécanisme d'auto-gestion à inventer (hors périmètre P0).
- Route publique `/verify/receipt/:id` (hors `authGuard`) confirme qu'un modèle de route publique
  pour un tiers non authentifié existe déjà et est réutilisable si une future consultation
  publique de statut de notification était envisagée (non retenue au P0).

## 7. Impact Batch

`generer_alertes()` (SQL, `SECURITY DEFINER`) devra être étendue pour produire, dans la même
transaction, les lignes `notification_event`/`notification_outbox` correspondant aux alertes
`NON_LUE` nouvellement insérées (voie A, ADR-18 §2) — même mécanique `ON CONFLICT ... DO NOTHING`
que l'anti-doublon `uq_alerte_nonlue` déjà en place. `AlertesScheduler` (cron quotidien) et
`marquer_loyers_en_retard()` restent inchangés en tant que tels. Un nouveau
`NotificationDispatcher` (`@Scheduled`, cadence configurable) est requis pour le traitement de
l'Outbox — vit dans le processus `api` existant, aucun nouveau service Compose nécessaire au P0.

## 8. Impact Audit

`AuditService.enregistrer(...)` (patron déjà en place) trace les actions applicatives sur les
préférences (création, modification, désinscription) — aucune modification du mécanisme d'audit
lui-même. L'écriture `notification_event` dans les transactions métier existantes (voie B, ADR-18
§2) suit exactement le même patron d'insertion native intra-transaction déjà utilisé pour
`audit_log`.

## 9. Impact Observabilité

`docs/cgpa/observability-governance.md` documente aujourd'hui explicitement l'absence de service
externe critique dans l'architecture (« hors périmètre actuel ») — cette évolution **change ce
périmètre** et devra faire l'objet d'une extension additive (nouvelle ligne OBS-03, nouvelles
alertes Alertmanager `component: notifications`) au Plan d'Exécution, avant toute activation
Production. Nouvelles métriques `notification.*` (voir ADR-18 §Coûts) suivant la convention
dot-notation déjà en place (`quittance.verifications{resultat}`), avec initialisation
`@PostConstruct` (sinon la série n'apparaît pas au premier scrape, leçon déjà tracée pour
`QuittanceMetrics`/`BatchMetrics`).

## 10. Impact Twilio (fournisseur externe)

Premier fournisseur externe critique introduit dans ce projet au-delà de Keycloak/GHCR. Nécessite
: compte Twilio (hors périmètre de cette mission — interdiction explicite de créer un compte),
templates WhatsApp Business à faire approuver (hors périmètre, formalisés seulement), numéro
expéditeur dédié par environnement.

## 11. Impact Secrets

Sept variables nouvelles (`TWILIO_*`), toutes hors dépôt, convention `${VAR}` déjà en place. Aucune
modification de `.env.example` dans cette mission (voir §17 du prompt — pas de convention CGPA
identifiée exigeant de documenter des variables dès la phase d'architecture pour une fonctionnalité
non encore implémentée ; le précédent `QUITTANCE_HMAC_SECRET` a été ajouté à
`.env.example` **au moment de l'implémentation réelle** d'EP-14, pas à son cadrage).

## 12. Impact Staging

Aucun déploiement Staging engagé par cette mission. Au Plan d'Exécution : Gate Staging (dont
`STG-ISOL-01`, hôte mutualisé `ai-test-server`) requis avant toute promotion, avec les feature
flags `NOTIFICATIONS_EXTERNAL_ENABLED=false` par défaut — un Twilio Sandbox (numéro de test
Twilio, pas de compte de production) serait la cible technique pour la validation Staging du P0.

## 13. Impact Production

Aucun déploiement Production engasé par cette mission. Recommandation de principe reprise de la
mission : ne jamais promouvoir en Production un lot capable d'émettre des messages réels tant que
consentement, idempotence, callbacks, plafond budgétaire, observabilité et kill switch ne sont pas
opérationnels (cf. K8).

## 14. Impact Coûts

Twilio facture au message (WhatsApp et SMS) — premier coût d'exploitation variable de ce projet
au-delà de l'hébergement fixe (EC2, GHCR). Plafond mensuel configurable obligatoire avant toute
activation réelle (EF-124, RM-120).

## 15. Impact Rollback

Migrations additives uniquement — rollback applicatif trivial (redéploiement du tag précédent).
Kill switch opérationnel (`NOTIFICATIONS_EXTERNAL_ENABLED=false`) comme filet de sécurité
supplémentaire à tout rollback applicatif, activable sans redéploiement.

## 16. Impact Exploitation

Nouveau runbook requis (US-126) : procédure d'incident Twilio (dégradation API, erreurs
massives), procédure de reprise manuelle d'une notification `DEAD`, procédure de rotation des
secrets Twilio (distincte de la rotation Keycloak déjà documentée).

## 17. Dépendances avec EP-14 et EP-15

- **EP-14 (Quittances certifiées, ADR-15)** : réutilisation directe et sans modification du
  mécanisme de lien HMAC (`TokenQuittanceService`, `PublicQuittanceController`) pour
  `QUITTANCE_DISPONIBLE` — aucune dépendance technique bloquante, simple consommation en lecture.
- **EP-15 (Gestion des personnes, ADR-16)** : `NotificationPreference.recipient_id` référence
  potentiellement un `Locataire` (US-113/114, Sprint C, `1.12.0`) ou un `Gestionnaire` (Sprints A/B).
  Aucune dépendance bloquante — EP-15 est déjà `PRODUCTION_DEPLOYED`, les entités existent. Le
  point d'attention est fonctionnel, pas technique : le `Locataire` n'a aucun compte applicatif
  (cf. §6), ce qui limite l'auto-gestion de ses propres préférences au P0.
- **EP-13 (Fin de bail, ADR-17)** : aucune dépendance directe. Signalé uniquement parce que la
  collision de numérotation US-115→118 provient de cet epic (cf. addendum backlog EP-16 §Note de
  numérotation).

---

## Risques (classement)

| Risque | Niveau | Détail |
|---|---|---|
| RSV-EP16-01 — Double alimentation de l'Outbox mal isolée | **Majeur** | Un même fait métier pourrait produire deux événements (voie batch + voie transactionnelle) si la séparation par `event_type` n'est pas strictement respectée — à verrouiller par conception avant codage |
| RSV-EP16-02 — Traitement concurrent de l'Outbox | **Majeur** | Premier pattern de « travail en cours » concurrent de ce projet — nécessite `FOR UPDATE SKIP LOCKED` + contrainte unique, test de concurrence dédié |
| RSV-EP16-04 — Consentement absent ou mal recueilli | **Majeur** | Risque réglementaire (WhatsApp Business Policy, RGPD) si un message est envoyé sans opt-in explicite et daté |
| RSV-EP16-05 — Observabilité Twilio non couverte | **Modéré** | `observability-governance.md` doit être étendu avant toute activation Production |
| RSV-EP16-03 — Dérive budgétaire | **Modéré** | Absence de plafond opérationnel dès le premier envoi réel |
| RSV-EP16-06 — Référentiel de templates global insuffisant | **Acceptable** | Aucune preuve actuelle d'un besoin de personnalisation par bailleur |
| Aucun compte Twilio, aucun SDK, aucun secret Twilio créés par cette mission | **Bloquant (respecté)** | Conforme aux interdictions strictes de la mission — aucune action de ce type n'a été entreprise |
