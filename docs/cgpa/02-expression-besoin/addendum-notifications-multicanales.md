# Addendum EB — Notifications multicanales via Twilio (EP-16)

| Champ | Valeur |
|-------|--------|
| Document de référence | `expression-besoin.md` v1.2 (✅ Validé — Gate 1 Go, 2026-06-04) — **non modifié** |
| Statut de l'addendum | **Proposé** — cadrage documentaire (analyse d'impact) ; aucun codage engagé. K1→K8 (ADR-18) entièrement ouverts, aucune décision PO encore rendue |
| Date | 2026-07-19 |
| Décision liée | `ADR-18-notifications-multicanales-twilio.md` (Proposée) |
| Principe | Additif — n'invalide, ne rejoue ni ne modifie le Gate 1 Go déjà statué |

> **Note de convention documentaire.** Le prompt de mission suggérait un document unique
> `docs/addendum-notifications-multicanales.md` à la racine de `docs/`. Après vérification des
> conventions réelles du dépôt, tous les précédents (EP-13 Fin de bail, EP-09/EP-15 Patrimoine et
> Personnes) séparent systématiquement un addendum EB (`docs/cgpa/02-expression-besoin/`) et un
> addendum CDC (`docs/cgpa/04-cahier-des-charges/`). Ce document suit donc la convention réelle du
> dépôt plutôt que le chemin unique suggéré — divergence documentée ici, cf. rapport final de
> mission §C.

> Cet addendum étend le périmètre de l'EB v1.2 sans en altérer le contenu validé. Il introduit les
> nouveaux besoins fonctionnels BF-106→BF-111.

## 1. Extension du périmètre (EB §2.1)

Le périmètre inclus s'enrichit de :

- **Notification externe (WhatsApp, secours SMS) des événements clés du cycle locatif**
  (quittance disponible, loyer en retard, garantie débitée — P0 ; paiement reçu, garantie à
  reconstituer, fin de bail approchante, préavis, invitation gestionnaire, bail créé/clos — P1),
  en complément des alertes in-app existantes (US-50/51/52), jamais en remplacement (BF-106).
- **Recueil et respect du consentement/préférences de canal** par destinataire — coordonnée
  téléphonique, canal préféré, canal de secours, opt-in WhatsApp, opt-in SMS, désinscription
  (BF-107).
- **Indépendance stricte des opérations financières et métier vis-à-vis du fournisseur externe** :
  aucune indisponibilité Twilio ne doit jamais bloquer un paiement, un débit de garantie, une
  création de bail ou une émission de quittance (BF-108).
- **Suivi de livraison et reprise en cas d'échec** — statut par tentative, retry maîtrisé,
  dead-letter, reprise manuelle (BF-109).
- **Fallback SMS maîtrisé et budgété**, jamais automatique sans politique explicite, avec plafond
  de coût (BF-110).
- **Historique des notifications consultable** par le destinataire autorisé (BF-111).

> **Constat opérationnel motivant ce cadrage** : LoyerTracker ne dispose aujourd'hui d'aucun canal
> de communication externe — seules des alertes in-app (consultées en se connectant au dashboard)
> existent. Aucun mécanisme de consentement, de préférence ou de coordonnée de notification
> n'existe dans le code (recherche exhaustive backend + frontend, confirmée par trois
> explorations factuelles). Ce n'est pas une extension d'un système existant, mais l'introduction
> d'une capacité entièrement nouvelle.

> Ne reconduit **pas** d'exclusion EB §2.2 : aucun élément du périmètre exclu (quittance PDF hors
> lien sécurisé déjà géré par EP-14, mandat, IRL, rapprochement bancaire, multi-bailleur/SCI,
> paiement en ligne, frais exceptionnels, application mobile) n'est concerné par cette évolution.
> **n8n et tout orchestrateur externe sont explicitement exclus** de ce périmètre (ADR-18 §Options,
> point 3) — pourront être réétudiés séparément.

## 2. Nouveaux besoins fonctionnels

| ID | Besoin | Priorité | Lié à |
|----|--------|----------|-------|
| BF-106 | Notifier par un canal externe (WhatsApp, secours SMS) les événements clés du cycle locatif, en complément — jamais en remplacement — des alertes in-app existantes. | Must | ADR-18 §Décision |
| BF-107 | Recueillir, dater, sourcer et respecter le consentement de chaque destinataire par canal (WhatsApp, SMS), y compris la désinscription. | Must | ADR-18 §Consentement — **proposition, à confirmer (K3)** |
| BF-108 | Garantir qu'aucune indisponibilité du fournisseur de notification externe n'affecte une opération métier ou financière. | Must | ADR-18 §Décision, points 1-3 |
| BF-109 | Assurer un suivi de livraison par destinataire/canal (statuts fournisseur), avec reprise maîtrisée en cas d'échec temporaire et arrêt définitif en cas d'échec permanent. | Should | ADR-18 §Statuts |
| BF-110 | Permettre un envoi de secours par SMS, budgété et non automatique par défaut. | Should | ADR-18 §Décision point 6 — **proposition, à confirmer (K5)** |
| BF-111 | Donner accès à un historique des notifications envoyées, limité au périmètre d'autorisation du consultant. | Could | ADR-18 §Sécurité — **proposition, à confirmer (K6)** |

## 3. Parties prenantes — précision de l'EB §3

Extension potentielle du rôle **Locataire** (jusqu'ici jamais destinataire direct d'une
communication proactive du produit — seul un accès de vérification de quittance non authentifié
existe, EP-14) vers un rôle de **destinataire de notification**, sous réserve de consentement
explicite. Le rôle **Gestionnaire** peut également devenir destinataire (suivi opérationnel).
Arbitrage exact des destinataires par événement : K1 (voir rapport de mission).

## 4. Matrice de rôles & permissions — extension de l'EB §6

| Action | Bailleur | Gestionnaire | Locataire |
|--------|----------|--------------|-----------|
| Recevoir une notification externe (sous consentement) | ✅ | ✅ (selon K1) | ✅ (selon K1) |
| Gérer ses propres préférences de notification | ✅ | ✅ | ✅ (selon K1/canal d'accès — le Locataire n'a aujourd'hui aucun compte LoyerTracker) |
| Consulter l'historique des notifications d'un tiers de son tenant | ✅ (son tenant uniquement) | ❌ (son propre historique seulement) | ❌ |

> **Point ouvert non trivial** : le `Locataire` n'a aujourd'hui **aucun compte applicatif**
> (aucune authentification, aucun accès self-service) — seule la vérification publique de
> quittance (EP-14) l'atteint sans authentification. La gestion de ses propres préférences
> nécessite donc soit une saisie par le bailleur pour son compte (comme la création rapide de
> `Locataire` déjà en place, EP-15 Sprint C), soit un mécanisme d'auto-gestion à inventer (hors
> périmètre du P0, cf. rapport de mission K4).

## 5. Hypothèses à valider (complète EB §9)

- [ ] K1 (ADR-18) : quels rôles sont destinataires de quels événements — proposition par défaut :
  locataire pour quittance/garantie/retard, bailleur et gestionnaire pour suivi opérationnel, selon
  autorisations et préférences.
- [ ] K2 (ADR-18) : canal principal — proposition par défaut : IN_APP obligatoire, WHATSAPP
  principal, SMS secours.
- [ ] K3 (ADR-18) : mode de recueil du consentement initial — formulaire LoyerTracker, preuve
  externe saisie par le bailleur, invitation du destinataire, ou vérification OTP ultérieure —
  aucune proposition par défaut, arbitrage PO requis.
- [ ] K4 (ADR-18) : stratégie de numéro — réutiliser le numéro existant du Locataire/Gestionnaire,
  ajouter un numéro dédié, ou exiger une vérification préalable.
- [ ] K5 (ADR-18) : fallback SMS — proposition par défaut : aucun fallback automatique au premier
  pilote.
- [ ] K6 (ADR-18) : qui peut consulter l'historique (messages, statuts, erreurs, coûts,
  tentatives) — à trancher.
- [ ] K7 (ADR-18) : durée de rétention des métadonnées de livraison (90 j / 180 j / 1 an /
  alignement audit) — à trancher.
- [ ] K8 (ADR-18) : stratégie de release — proposition par défaut : déployer le socle désactivé,
  valider Staging, puis activer progressivement après validation complète du P0.

## 6. Non-régression

Aucune des hypothèses validées de l'EB v1.2 (§9) n'est remise en cause. Le scope EB §2.2 reste
inchangé et hors périmètre. La matrice de rôles §6 existante n'est pas modifiée — seules les
lignes relatives aux notifications externes sont ajoutées (§4 ci-dessus). Les alertes in-app
existantes (US-50/51/52) restent inchangées dans leur fonctionnement, leur anti-doublon et leur
scoping bailleur/gestionnaire.
