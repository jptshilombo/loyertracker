# Addendum EB — Introduction de la notion de Patrimoine

| Champ | Valeur |
|-------|--------|
| Document de référence | `expression-besoin.md` v1.2 (✅ Validé — Gate 1 Go, 2026-06-04) — **non modifié** |
| Statut de l'addendum | Proposé — en attente d'approbation PO, aucun codage engagé |
| Date | 2026-06-21 |
| Décision liée | D-PAT-001 (`docs/cgpa/05-architecture-conception/adr/ADR-11-introduction-patrimoine.md`) |
| Principe | Additif — n'invalide, ne rejoue ni ne modifie le Gate 1 Go déjà statué |

> Cet addendum étend le périmètre de l'EB v1.2 sans en altérer le contenu validé. Il introduit les besoins fonctionnels BF-90→BF-96 et précise la matrice de rôles §6 de l'EB pour la nouvelle granularité Patrimoine.

## 1. Extension du périmètre (EB §2.1)

Le périmètre inclus s'enrichit de :

- **Gestion des patrimoines** : un bailleur peut regrouper ses biens en un ou plusieurs patrimoines (BF-90).
- **Typologie administrable des biens** : remplace la saisie libre actuelle du type de bien (BF-91).
- **Rattachement obligatoire bien → patrimoine** (BF-92).
- **Affectation gestionnaire à deux granularités** : patrimoine entier ou bien spécifique, avec règles de priorité et d'exceptions d'inclusion/exclusion (BF-93/94/95).
- **Clarification terminologique** : le terme produit « **Contrat** » désigne l'entité déjà existante `Bail` (BF-12/EF-12, statuts `ACTIF`/`CLOS`) ; aucune nouvelle règle de gestion sur l'unicité du contrat actif n'est introduite (BF-96, déjà couverte).

> Ne reconduit **pas** l'exclusion EB §2.2 « Multi-bailleur sur un même compte / SCI multi-associés (Won't) » : un patrimoine reste la propriété d'un **seul** bailleur (règle métier #2). Cette décision ne réouvre donc pas le scope « multi-bailleur ».

## 2. Nouveaux besoins fonctionnels

| ID | Besoin | Priorité | Lié à |
|----|--------|----------|-------|
| BF-90 | Le bailleur peut créer, renommer et archiver un ou plusieurs **patrimoines**, chacun regroupant un sous-ensemble de ses biens. | Must | Règle #1 |
| BF-91 | Chaque bien possède un **type** choisi dans une liste fermée mais **administrable** (`Appartement`, `Boutique`, `Bureau`, `Villa`, `Terrain`, `Entrepôt`, `Autre`) ; la liste peut évoluer sans déploiement applicatif. | Must | Règle #5 |
| BF-92 | Un bien est **obligatoirement** rattaché à un patrimoine unique ; un patrimoine appartient à un seul bailleur. | Must | Règles #2/#3/#4 |
| BF-93 | Le bailleur peut affecter un gestionnaire à **l'ensemble d'un patrimoine** (accès hérité à tous ses biens présents et futurs). | Must | Règle #8 |
| BF-94 | Le bailleur peut affecter ou restreindre un gestionnaire sur **un bien précis**, en complément (`INCLUSION`) ou en restriction (`EXCLUSION`) d'une affectation patrimoine existante. | Must | Règles #9/#10 |
| BF-95 | En cas de conflit entre une affectation patrimoine et une affectation bien pour le même couple (gestionnaire, bien), l'affectation **bien** prévaut. | Must | Règle #9 |
| BF-96 | *(Clarification, pas de nouveau besoin)* Le système garantit toujours un seul contrat (`Bail`) actif par bien — règle déjà satisfaite par BF-12/EF-12. | — | Règles #6/#7 (déjà couvert) |

## 3. Matrice de rôles & permissions — extension de l'EB §6

| Action | Bailleur | Gestionnaire (affectation patrimoine) | Gestionnaire (affectation bien) |
|--------|----------|----------------------------------------|----------------------------------|
| Créer / renommer / archiver un patrimoine | ✅ | ❌ | ❌ |
| Voir la liste des biens d'un patrimoine affecté | ✅ (tous) | ✅ (tous les biens du patrimoine, sauf `EXCLUSION` ciblée) | ✅ (uniquement les biens en `INCLUSION`) |
| Agir sur un bien (bail, paiement, garantie) | ✅ | ✅ si bien du patrimoine et hors `EXCLUSION` | ✅ si bien en `INCLUSION` ACTIVE |
| Administrer la liste des types de bien | ✅ (bailleur) — *à confirmer si réservé à un rôle « Admin » distinct, cf. réserve §4 | ❌ | ❌ |

## 4. Hypothèses à valider (complète EB §9)

- [ ] Un gestionnaire peut cumuler une affectation **patrimoine** sur P1 et une affectation **bien** (`INCLUSION`) sur un bien d'un **autre** patrimoine P2, sans affectation patrimoine sur P2.
- [ ] La liste des types de biens est administrée par le **bailleur lui-même** (pas de rôle « Administrateur » distinct du périmètre RBAC actuel `BAILLEUR`/`GESTIONNAIRE`) — **à confirmer avec le PO**, cf. ADR-11 §Risques.
- [ ] Un patrimoine peut rester vide (0 bien) sans être bloquant pour une affectation patrimoine.
- [ ] La suppression/archivage d'un patrimoine non vide est hors périmètre de cette décision (à traiter comme règle de gestion complémentaire si besoin).

## 5. Non-régression

Aucune des hypothèses validées de l'EB v1.2 (§9) n'est remise en cause. Le scope EB §2.2 (quittance/mandat PDF, IRL, rapprochement bancaire, multi-bailleur/SCI, paiement en ligne, alertes e-mail, frais exceptionnels) reste inchangé et hors périmètre.
