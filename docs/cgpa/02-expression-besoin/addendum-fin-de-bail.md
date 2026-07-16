# Addendum EB — Fin de bail (clôture effective d'un `Bail`)

| Champ | Valeur |
|-------|--------|
| Document de référence | `expression-besoin.md` v1.2 (✅ Validé — Gate 1 Go, 2026-06-04) — **non modifié** |
| Statut de l'addendum | **Proposé** — cadrage documentaire (analyse d'impact) ; aucun codage engagé. Kickoff K1→K6 ouvert (`docs/cgpa/05-architecture-conception/adr/ADR-17-fin-de-bail.md`), aucune décision PO encore rendue |
| Date | 2026-07-16 |
| Décision liée | `ADR-17-fin-de-bail.md` (Proposée) |
| Principe | Additif — n'invalide, ne rejoue ni ne modifie le Gate 1 Go déjà statué |

> Cet addendum étend le périmètre de l'EB v1.2 sans en altérer le contenu validé. Il introduit
> les besoins fonctionnels BF-102→BF-105.

## 1. Extension du périmètre (EB §2.1)

Le périmètre inclus s'enrichit de :

- **Clôture effective d'un bail** (`ACTIF → CLOS`), action bailleur explicite distincte de la
  date de fin contractuelle déjà existante (BF-102).
- **Réouverture d'un bail clos par erreur** (`CLOS → ACTIF`), sous réserve qu'aucun autre bail
  actif n'existe déjà sur le même bien (BF-103).
- **Avertissements non bloquants** à la clôture si la garantie n'est pas totalement restituée ou
  si des impayés subsistent (BF-104) — sous réserve de confirmation PO (K3/K4, cf. ADR-17).
- **Nettoyage de l'échéancier futur** déjà généré au-delà de la clôture (BF-105) — sous réserve
  de confirmation PO (K6, cf. ADR-17).

> **Constat opérationnel motivant ce cadrage** : la fonctionnalité de clôture est aujourd'hui
> **totalement absente** — le statut `CLOS` existe dans le schéma mais n'est jamais assigné,
> rendant impossible la création d'un second bail sur un même bien une fois le premier créé
> (contrainte d'unicité déjà en place, EF-12). Ce n'est pas une extension de confort, mais une
> lacune de cycle de vie déjà active en Production.

> Ne reconduit **pas** d'exclusion EB §2.2 : aucun élément du périmètre exclu (quittance PDF,
> mandat, IRL, rapprochement bancaire, multi-bailleur/SCI, paiement en ligne, alertes e-mail,
> frais exceptionnels, application mobile) n'est concerné par cette évolution.

## 2. Nouveaux besoins fonctionnels

| ID | Besoin | Priorité | Lié à |
|----|--------|----------|-------|
| BF-102 | Le bailleur peut **clôturer** un bail actif à tout moment (avant, à, ou après sa date de fin contractuelle), enregistrant une date de clôture effective distincte de celle-ci. | Must | ADR-17 K1/K2 |
| BF-103 | Le bailleur peut **rouvrir** un bail clos par erreur, sous réserve qu'aucun bail actif n'existe déjà sur le même bien. | Should | ADR-17 K5 |
| BF-104 | À la clôture, le système avertit (sans bloquer) si la garantie n'est pas totalement restituée ou si des paiements sont impayés/en retard. | Must | ADR-17 K3/K4 — **proposition, à confirmer** |
| BF-105 | À la clôture, les échéances de loyer déjà générées pour des périodes futures et non encore exigibles sont retirées de l'échéancier. | Should | ADR-17 K6 — **proposition, à confirmer** |

## 3. Parties prenantes — précision de l'EB §3

Aucun changement des parties prenantes (EB §3) : la clôture reste une action du **Bailleur**
uniquement, comme la création de bail (EF-12). Aucun rôle nouveau, aucune évolution du périmètre
du Gestionnaire ou du Locataire.

## 4. Matrice de rôles & permissions — extension de l'EB §6

| Action | Bailleur | Gestionnaire |
|--------|----------|--------------|
| Clôturer un bail | ✅ (biens dont il est propriétaire) | ❌ |
| Rouvrir un bail clos | ✅ | ❌ |
| Consulter le statut/historique de clôture d'un bail | ✅ | ✅ (biens affectés actifs, lecture seule — comme aujourd'hui pour la consultation de bail) |

## 5. Hypothèses à valider (complète EB §9)

- [ ] K1 (ADR-17) : la clôture est exclusivement manuelle, aucun automatisme sur `dateFin`
  dépassée — proposition par défaut, à trancher par le PO.
- [ ] K2 (ADR-17) : une date de clôture effective distincte de `dateFin` est nécessaire —
  proposition par défaut, à trancher.
- [ ] K3 (ADR-17) : garantie non restituée à la clôture → avertissement, pas de blocage —
  proposition par défaut, **à confirmer explicitement** (obligation légale/financière envers le
  locataire sortant, contrairement à un simple doublon probable).
- [ ] K4 (ADR-17) : impayés à la clôture → avertissement, pas de blocage — proposition par défaut,
  à confirmer.
- [ ] K5 (ADR-17) : réouverture autorisée sous réserve d'absence de bail actif concurrent —
  proposition par défaut, à trancher.
- [ ] K6 (ADR-17) : purge des échéances futures non exigibles au-delà de la clôture — proposition
  par défaut, à confirmer (certains bailleurs pourraient préférer conserver la trace).

## 6. Non-régression

Aucune des hypothèses validées de l'EB v1.2 (§9) n'est remise en cause. Le scope EB §2.2 reste
inchangé et hors périmètre. La matrice de rôles §6 existante n'est pas modifiée — seules les
lignes relatives à la clôture/réouverture de bail sont ajoutées (§4 ci-dessus).
