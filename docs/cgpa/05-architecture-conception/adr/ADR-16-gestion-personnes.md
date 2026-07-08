# ADR-16 — Gestion des personnes : Gestionnaires et Locataires comme entités durables (EP-15)

| Champ | Valeur |
|---|---|
| Code de décision | **D-PERS-001** |
| Statut | **Acceptée — kickoff clos** : décisions D1/D3/D5/D8 et point K1 tranchés par le PO le 2026-07-08 (K1 le 2026-07-08). Reste requis avant Sprint A : GO explicite du PO sur `plan-execution-ep15-personnes.md` |
| Date | 2026-07-08 |
| Phase | 05 — Architecture (cadrage post-`1.9.0`, avant tout codage) |
| Documents liés | `addendum-personnes.md` (EB), `addendum-personnes.md` (CDC), `addendum-backlog-ep15-personnes.md`, `plan-execution-ep15-personnes.md`, ADR-01 (cloisonnement multi-tenant), ADR-02 (RBAC/ReBAC), ADR-03 (RGPD by design), ADR-10 (intégration IdP gestionnaire), ADR-15 (précédent SECURITY DEFINER / bytea) |

## Contexte

Aujourd'hui :

- **Le Locataire n'existe pas comme entité.** `bail.locataire_nom`/`locataire_email` sont du
  texte libre porté par `Bail` depuis la V1. Aucune classe `Locataire*`, aucune table dédiée.
  L'anonymisation RGPD (ADR-03) écrase ces deux colonnes via `Bail.anonymiserLocataire()`.
- **Le Gestionnaire est une entité globale multi-bailleur** (`id, keycloak_id, email, nom,
  prenom`), **sans colonne de statut ni RLS** — décision assumée d'ADR-01/EF-05 : un même
  compte peut être réutilisé par plusieurs bailleurs indépendants. « Actif » est aujourd'hui
  **virtuel** : dérivé de l'existence d'une `Affectation` `ACTIVE`. Révoquer une affectation
  **ne désactive jamais** le compte Keycloak (BF-22/US-24) — révocation d'affectation et
  suspension de compte sont deux choses distinctes aujourd'hui, et le resteront.
- **Aucun endpoint d'administration directe** n'existe pour le Gestionnaire ; son compte est
  créé uniquement par le flux d'invitation (`AcceptationService`).

Le PO (EP-15, « Gestion des personnes ») demande que ces deux notions deviennent des entités de
domaine durables avec cycle de vie (`ACTIVE`/`SUSPENDU`/`ARCHIVE` pour le Gestionnaire,
`ACTIVE`/`ARCHIVE` pour le Locataire), historique, audit et anti-doublons — sans jamais de
suppression physique.

## Problème

1. Le Gestionnaire étant **global et multi-bailleur par construction** (ADR-01), quelle est la
   **portée** d'un statut `SUSPENDU`/`ARCHIVE` : par bailleur, ou par compte ?
2. Comment garantir « aucune affectation `ACTIVE` avant archivage » d'un Gestionnaire si le
   statut est global, sachant que la table `affectation` est **sous RLS `bailleur_isolation`**
   (une session bailleur ne voit que ses propres lignes) ?
3. Comment faire du Locataire une entité indépendante **sans** rouvrir la décision Gate-1
   validée (`expression-besoin.md` §3) qui le classe comme « sujet de données, non-utilisateur
   au MVP » ?
4. Comment migrer l'historique (`bail.locataire_nom`/`locataire_email`, texte libre, un seul
   champ non séparé nom/prénom) vers une entité structurée, sans risque de fusionner à tort
   deux personnes différentes ni de perdre l'historique ?
5. Où stocker la photo optionnelle des deux entités, sachant qu'il n'existe aujourd'hui aucune
   infrastructure de fichiers/objets ?

## Décision

### D1 — Statut Gestionnaire : portée **globale** (compte unique)

Le statut `ACTIVE`/`SUSPENDU`/`ARCHIVE` est porté par une **colonne globale** sur la table
`gestionnaire` (pas par la relation bailleur↔gestionnaire). **Conséquence assumée et actée par
le PO le 2026-07-08** : suspendre ou archiver un Gestionnaire l'empêche de se connecter pour
**tous** les bailleurs qui l'emploient, y compris ceux ayant avec lui une `Affectation`
`ACTIVE` sans rapport avec l'action engagée. Ceci est un écart réel par rapport au modèle
multi-bailleur strict d'ADR-01/EF-05 (un compte partagé, indépendant par bailleur) — **ADR-01
n'est pas réécrit** : il reste vrai que le Gestionnaire est multi-bailleur et hors RLS ; ce
document ajoute une **couche de statut de compte global**, orthogonale au périmètre d'accès
par bailleur (qui reste porté par `Affectation`).

Ce choix implique aussi que le **profil partagé** (téléphone, photo, observations) est
mutable par **n'importe quel bailleur ayant une relation** (affectation active ou passée) avec
ce Gestionnaire — pas seulement celui qui l'a « créé » au sens invitation. Voir RSV-EP15-01.

`SUSPENDU` **n'exige aucune pré-condition** (immédiat, réversible : « reste restaurable
immédiatement », règle métier PO). `ARCHIVE` **exige l'absence de toute `Affectation`
`ACTIVE`, tous bailleurs confondus** (règle métier PO) — voir D4 pour le mécanisme technique.

`SUSPENDU`/`ARCHIVE` désactive le compte Keycloak (`enabled=false`, pattern déjà éprouvé pour
`bailleur-test`, cf. `docs/prod-state.md`) ; `RESTAURER` le réactive. C'est cohérent uniquement
parce que la portée est globale — une portée par-bailleur n'aurait pas pu s'appuyer sur un
simple flag Keycloak sans casser l'accès des autres bailleurs.

### D2 — Locataire : nouvelle entité de domaine, **avec** `bailleur_id` et RLS

Contrairement au Gestionnaire, un Locataire est **intrinsèquement lié à un seul bailleur**
(aucune notion d'usage multi-bailleur analogue à EF-05). Il porte donc `bailleur_id NOT NULL`
et une policy RLS `bailleur_isolation` **FORCE**, strictement conforme à ADR-01 (« toute table
métier porte `bailleur_id` »). Statuts : `ACTIVE`/`ARCHIVE` (pas de `SUSPENDU` — absent des
règles métier PO pour le Locataire).

**Le Locataire ne devient PAS un compte utilisateur** : aucune identité Keycloak, aucun rôle
RBAC, aucune connexion, aucun JWT. Ceci **précise** la décision Gate-1 (`expression-besoin.md`
§3 : « sujet de données, non-utilisateur au MVP ») **sans la contredire ni la réécrire** : le
sujet de données passe d'un champ texte embarqué dans `Bail` à une entité de domaine durable,
strictement dans le même périmètre RGPD.

### D3 — Modèle de données (V23 additive, V24 non additive)

**V23 (additive)** :
- Table `locataire` : `id`, `bailleur_id NOT NULL`, `nom`, `prenom`, `telephone`, `email`,
  `profession`, `date_naissance`, `type_piece_identite`, `numero_piece_identite`, `photo
  BYTEA NULL`, `contact_urgence`, `observations`, `statut` (`ACTIVE`|`ARCHIVE`),
  `date_creation`, `date_archivage NULL`. RLS `ENABLE`+`FORCE`, policy `bailleur_isolation`
  (pattern V1/V12/V20/V22).
- Colonnes ajoutées à `gestionnaire` (jusqu'ici plate depuis la V1) : `statut`
  (`ACTIVE`|`SUSPENDU`|`ARCHIVE`, `DEFAULT 'ACTIVE'`), `telephone`, `photo BYTEA NULL`,
  `date_creation` (`DEFAULT now()`, rétroactivement renseignée au déploiement pour les comptes
  existants), `date_suspension NULL`, `date_archivage NULL`, `observations`. **Pas de RLS**
  (inchangé — cohérent avec ADR-01/le caractère global de la table).
  Attention: `gestionnaire.telephone`/`photo` etc. sont des colonnes **partagées entre
  bailleurs** (cf. D1) — ce n'est pas un oubli, c'est la conséquence assumée de la portée
  globale.
- `bail.locataire_id UUID NULL REFERENCES locataire(id)` — nullable en V23 (transition).

**V24 (non additive — cutover)**, livrée dans un sprint séparé après bake-in de V23 :
- Backfill : pour chaque `bail` existant, création d'un `locataire` (`bailleur_id =
  bail.bailleur_id`, `nom = bail.locataire_nom`, `email = bail.locataire_email`) et mise à
  jour de `bail.locataire_id` — **1 Locataire par bail historique, aucune déduplication
  automatique** (décision PO 2026-07-08 : plus sûr, pas de risque de fusion erronée ; un même
  locataire ayant eu plusieurs baux successifs apparaîtra en plusieurs fiches jusqu'à un
  rapprochement manuel ultérieur, hors périmètre EP-15).
- **`prenom` non peuplé par le backfill** : `bail.locataire_nom` est un champ unique historique
  (jamais séparé nom/prénom) — tenter un découpage automatique serait non fiable (ordres
  variables, noms composés). Le backfill copie l'intégralité de la valeur dans `nom` et laisse
  `prenom` vide ; une correction manuelle ultérieure est possible sans impact fonctionnel
  (voir RSV-EP15-02).
- `bail.locataire_id` passe `NOT NULL` puis **suppression de `bail.locataire_nom` et
  `bail.locataire_email`** (décision PO 2026-07-08). **Migration non additive** : un rollback
  vers une version antérieure à V24 **n'est pas viable applicativement** une fois les colonnes
  supprimées — seule une restauration de backup le permet (même profil de risque que V20,
  `RSV-S9-03`). Le Préflight de la release qui embarque V24 devra donc vérifier un backup
  post-migration immédiatement disponible, comme pour toute migration non additive du projet.

### D4 — Vérification cross-tenant « aucune affectation ACTIVE nulle part » (fonction `SECURITY DEFINER`)

`affectation` est sous RLS `bailleur_isolation` : une session applicative scopée à un bailleur
ne voit que ses propres lignes. Or le pré-check d'archivage (D1) doit couvrir **tous les
bailleurs**. Solution : une fonction SQL `gestionnaire_a_affectation_active(gestionnaire_id
UUID) RETURNS BOOLEAN`, `SECURITY DEFINER`, appartenant au rôle propriétaire de la table
(bypass RLS par ownership, **jamais** un `BYPASSRLS` accordé à `loyertracker_api`), qui
n'expose **aucune donnée** — seulement un booléen. C'est le même patron que les fonctions
publiques de lecture de quittance (V22, ADR-15 D5) : une fonction étroite, à surface minimale,
plutôt qu'une levée de la RLS.

### D5 — Photo : colonne `bytea` PostgreSQL (décision PO 2026-07-08)

Réutilisation directe du précédent `quittance.pdf` (déjà en `bytea`, déjà couvert par le
`pg_dump` existant, aucune nouvelle infrastructure fichier/objet). Colonne nullable sur
`gestionnaire` et `locataire`. Volumétrie PME faible, cohérente avec l'analyse déjà faite pour
`quittance.pdf` (ADR-15, alternatives écartées).

### D6 — RGPD : la cible de l'anonymisation se déplace vers `Locataire`

L'effacement RGPD (ADR-03) ne cible plus `Bail.locataireNom/Email` mais l'entité `Locataire`
elle-même : anonymiser un Locataire (nom → `[anonymisé]`, email/téléphone/pièce d'identité →
`null`) rend cohérent l'effacement pour **tous les baux historiques de la même personne** en
une seule opération — un vrai gain par rapport au mécanisme actuel qui n'anonymisait qu'un bail
à la fois. `RgpdService.anonymiserLocataire()` est réécrit pour cibler `Locataire`, avec
non-régression garantie sur les tests d'export/effacement RGPD existants (US dédiée au
backlog). Le Locataire archivé reste soumis aux mêmes règles RGPD qu'aujourd'hui : conservation
pour raison comptable, jamais de suppression physique (ADR-03 inchangé).

### D7 — Audit

Aucun audit n'existe aujourd'hui sur `gestionnaire`/`affectation`/`bien`/`patrimoine`. De
nouveaux points d'audit (`AuditService.enregistrer`) sont ajoutés, **sans toucher** aux points
existants (quittance/garantie/bail-RGPD/honoraire/paiement) : `CREER_GESTIONNAIRE_PROFIL`,
`MODIFIER_GESTIONNAIRE`, `SUSPENDRE_GESTIONNAIRE`, `REACTIVER_GESTIONNAIRE`,
`ARCHIVER_GESTIONNAIRE`, `RESTAURER_GESTIONNAIRE`, `CREER_LOCATAIRE`, `MODIFIER_LOCATAIRE`,
`ARCHIVER_LOCATAIRE`, `RESTAURER_LOCATAIRE`.

### D8 — Détection de doublons : portée différente selon l'entité

- **Locataire** : `Locataire` porte `bailleur_id` + RLS (D2) — une requête de doublon
  (email/téléphone/numéro de pièce d'identité) est **naturellement scopée au bailleur courant**
  ; aucune fuite cross-bailleur possible, aucun mécanisme spécial requis.
- **Gestionnaire** : la table n'a **aucune RLS** (inchangé, ADR-01) — une requête de doublon
  (email/téléphone) est donc **naturellement globale**, cohérente avec le caractère
  multi-bailleur de l'entité ; aucun mécanisme spécial requis non plus.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Statut Gestionnaire porté par la relation bailleur↔gestionnaire (par bailleur) | Recommandation initiale de l'architecte ; **écartée par le PO** au profit d'un statut global, plus simple à opérer pour un compte réellement partagé — risque cross-bailleur assumé (RSV-EP15-01) plutôt qu'ignoré |
| Déduplication automatique des Locataires historiques par email | Risque de fusionner à tort deux personnes distinctes partageant un email générique — écarté par le PO au profit d'un rapprochement manuel a posteriori |
| Conserver `bail.locataire_nom`/`locataire_email` comme instantané figé en plus de `locataire_id` | Redondance permanente, deux sources de vérité pour l'affichage — écarté par le PO au profit d'une coupe nette (pattern V20) |
| Stockage de la photo sur filesystem/S3 | Aucune infrastructure objet existante ; `bytea` réutilise un précédent déjà validé (`quittance.pdf`, ADR-15) et reste sous le backup `pg_dump` existant |
| Lever la RLS sur `affectation` pour le pré-check d'archivage (rôle `BYPASSRLS`) | Élargirait une surface de contournement RLS générale au lieu d'une fonction étroite à surface minimale (D4) — rejeté au profit du pattern `SECURITY DEFINER` déjà éprouvé (V22) |
| Rôle `ADMIN` applicatif dédié pour administrer les Gestionnaires | Aucun rôle `ADMIN` n'existe (RBAC actuel = `BAILLEUR`/`GESTIONNAIRE` uniquement) ; en créer un serait hors périmètre EP-15 et sans besoin exprimé par le PO — les actions restent réservées au rôle `BAILLEUR` existant |

## Conséquences

- `GestionnaireController` est un **nouveau** contrôleur (n'existe pas aujourd'hui) : profil,
  suspension, réactivation, archivage, restauration, historique, recherche, doublons.
- `LocataireController` est également nouveau (entité inexistante à ce jour).
- `Bail` change de contrat : `locataireNom`/`locataireEmail` (texte) disparaissent au profit de
  `locataireId` (V24) — **rupture de contrat HTTP à gérer explicitement** dans la Sprint qui
  porte V24 (versionnement ou communication du changement, à cadrer au Plan d'Exécution).
- `RgpdService`/`Bail.anonymiserLocataire()` sont réécrits (D6) — non-régression à prouver sur
  les tests RGPD existants.
- Le compte Keycloak du Gestionnaire peut désormais être désactivé par l'application
  (`enabled=false`) — première fois que ce mécanisme est déclenché applicativement (jusqu'ici
  seul `bailleur-test` avait été désactivé manuellement en exploitation).

## Impacts sécurité

- La fonction `SECURITY DEFINER` (D4) ne renvoie qu'un booléen — aucune donnée d'`affectation`
  exposée, surface minimale, cohérente avec le principe déjà appliqué en V22.
- Portée globale du statut Gestionnaire (D1) : **risque accepté** qu'un bailleur avec une
  relation, même passée, à un Gestionnaire partagé puisse le suspendre/archiver et affecter
  d'autres bailleurs sans préavis (RSV-EP15-01) — mitigation minimale : traçabilité complète
  (D7) permettant à tout bailleur impacté de constater l'action via son propre journal d'audit
  s'il y a accès (à vérifier au Plan d'Exécution si le journal doit notifier les bailleurs
  tiers, ou rester consultable a posteriori uniquement).
- Aucune nouvelle route publique (`permitAll`) — contrairement à EP-14, ce lot reste
  entièrement dans le périmètre authentifié `BAILLEUR`.

## Impacts RGPD (ADR-03)

- Le Locataire reste un sujet de données, jamais un compte (D2) — pas de nouvelle catégorie de
  données de connexion.
- L'anonymisation par personne (D6) est une **amélioration** par rapport au mécanisme actuel
  (anonymisation bail par bail) : un effacement demandé couvre tout l'historique de la
  personne en une opération.
- Nouvelle donnée sensible : `numero_piece_identite` (pièce d'identité) — à inclure
  explicitement dans le registre des traitements et dans le périmètre d'anonymisation RGPD
  (D6), au même titre que nom/email/téléphone.
- Photo (D5) : donnée personnelle sensible au sens large — mêmes règles d'anonymisation que le
  reste du profil (mise à `NULL` à l'effacement).

## Impacts performances

Ajout d'une jointure `bail → locataire` sur les parcours consultant le nom/contact du
locataire (dashboards, documents) — index `locataire(bailleur_id)` et `bail(locataire_id)` à
créer en V23 pour préserver les temps de réponse existants (ENF-06 historique, < 2 s / 50
biens). Volumétrie `bytea` (photos) faible pour un usage PME, même profil que `quittance.pdf`.

## Registre des risques (RSV-EP15)

| ID | Risque | Statut |
|---|---|---|
| RSV-EP15-01 | Portée globale du statut/profil Gestionnaire (D1) : un bailleur ayant une relation avec un Gestionnaire partagé peut le suspendre/archiver/modifier son profil, affectant tous les autres bailleurs qui l'emploient, sans préavis pour eux | **Accepté par le PO le 2026-07-08** — mitigation : traçabilité complète (D7), à préciser au Plan d'Exécution (visibilité pour les bailleurs tiers impactés) |
| RSV-EP15-02 | Le backfill V24 ne peut pas séparer fiablement `nom`/`prenom` depuis l'unique champ historique `bail.locataire_nom` | **Accepté** — `nom` reçoit la valeur intégrale, `prenom` reste vide pour les enregistrements migrés ; correction manuelle possible sans impact fonctionnel |
| RSV-EP15-03 | V24 (suppression de `bail.locataire_nom`/`locataire_email`) n'est **pas** un rollback applicatif viable — seule une restauration de backup permet un retour arrière | **Accepté** — même profil que `RSV-S9-03` (V20) ; Préflight de la release concernée doit vérifier un backup post-migration immédiatement disponible |
| RSV-EP15-04 | `BienService.archiver()` ne vérifie aujourd'hui aucune affectation/bail actif avant archivage (asymétrie avec `PatrimoineService.archiver()`) — découverte pendant l'exploration de ce cadrage | **Hors périmètre EP-15** — consigné comme dette technique existante, à corriger dans un lot dédié si le PO le priorise, **non traité silencieusement ici** |

## Points tranchés au kickoff (PO, 2026-07-08) — aucune décision implicite

| # | Question | Décision PO |
|---|---|---|
| K1 | Sémantique de « créer » un Gestionnaire : aucun flux de création directe n'existe aujourd'hui (seule l'invitation crée le compte technique lié à Keycloak, `AcceptationService`). Introduire un flux de création administrative en contournement de l'invitation, ou « créer » = compléter pour la première fois le profil métier (téléphone/photo/observations) d'un compte déjà créé par invitation ? | ✅ **Profil sur compte existant** — l'invitation reste l'unique voie de création du compte technique ; « créer » un Gestionnaire au sens de cet Epic = enrichir le profil d'un compte déjà existant. Aucun nouveau flux admin en contournement |

## Compatibilité et migration

- **V23 additive** : nouvelles tables/colonnes uniquement, `bail.locataire_id` nullable —
  rollback applicatif seul viable pour ce sprint (même profil que V21/V22).
- **V24 non additive** : cutover + suppression de colonnes — rollback applicatif **non
  viable**, restauration de backup requise (RSV-EP15-03, même profil que V20/RSV-S9-03). Livrée
  dans un sprint distinct après bake-in de V23 en Staging puis Production.
- Aucun changement sur les modules Patrimoine/Bien/Affectation/Garantie/Paiement/Honoraires/
  Alertes/Dashboard/Ledger/Money/Documents PDF/QR Code — non-régression à couvrir par la suite
  de tests existante, aucun endpoint de ces modules n'est modifié par ce lot (seul `Bail`
  perd `locataireNom`/`locataireEmail` en V24, remplacés par `locataireId`).
