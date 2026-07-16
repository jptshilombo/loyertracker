# ADR-17 — Fin de bail : clôture effective d'un `Bail` (EP-13)

| Champ | Valeur |
|-------|--------|
| Statut | **Proposée** — cadrage documentaire (analyse d'impact) ; aucun codage engagé |
| Date | 2026-07-16 |
| Origine | Instruction PO du 2026-07-16 (« cadrer EP-13 fin de bail ») ; identifiant EP-13 réservé depuis le cadrage EP-10→13 du 2026-07-01 (`analyse-impact-evolutions-ep10-ep13.md` §4, Évolution 7) |
| Principe | Additif — n'invalide, ne rejoue ni ne modifie aucun Gate, décision ou risque déjà statué |

## Contexte

`Bail.statut` est un `enum` à deux valeurs, `ACTIF`/`CLOS`, depuis le tout premier schéma du
projet (EF-12). **`CLOS` n'est aujourd'hui jamais assigné par aucun code applicatif** — vérifié
dans `BailService`/`Bail` : aucune méthode de clôture n'existe. Conséquence directe et déjà
active en Production : la contrainte d'unicité `uq_bail_actif` (« un bail actif au plus par
bien », EF-12, `existsByBienIdAndStatut(bienId, ACTIF)`) rend **impossible la création d'un
second bail sur un même bien** une fois le premier créé, puisque rien ne referme jamais le
premier. Ce n'est pas un simple point d'architecture à préparer : c'est une fonctionnalité de
cycle de vie manquante qui bloquera tout bailleur dont un locataire réel quitte les lieux.

`Bail.dateFin` existe déjà (date de fin **contractuelle/prévisionnelle**, saisie à la création du
bail) et alimente uniquement la génération des alertes `FIN_BAIL`/`PREAVIS` (EF-60/61/63,
US-50/51) — elle ne déclenche aujourd'hui aucune transition d'état.

`ADR-14` (Sprint 9, Garantie ledger) avait anticipé, en préparation d'architecture seule, que le
modèle `GarantieMovement` couvre nativement restitution totale/partielle et retenues, et
suggérait un futur `ClotureBailService` orchestrant plusieurs mouvements en une transaction —
non développé à ce stade. La restitution de garantie (`GarantieService.restituer`) fonctionne
déjà de façon totalement autonome, sans lien avec le statut du `Bail`.

**Dépendance de séquencement avec EP-15** : le Sprint C d'EP-15 (US-113, migration V25) fera de
`Locataire` la seule source de vérité du locataire d'un bail (aujourd'hui encore `Bail.locataireId`
nullable, préparé par V24 mais non exploité). La clôture de bail ne dépend pas de cette bascule
pour fonctionner, mais l'ordre de mise en Production doit être cohérent (cf. §Compatibilité).

## Problème

Concevoir la clôture effective d'un `Bail` (`ACTIF → CLOS`) sans développement anticipé au-delà de
ce cadrage, en couvrant :
1. Ce qui déclenche la transition (action bailleur, ou automatisation sur `dateFin`).
2. L'articulation avec la garantie (déjà restituable indépendamment) et les paiements en cours.
3. Le sort de l'échéancier futur déjà généré par le batch mensuel (US-30) au-delà de la clôture.
4. La réversibilité d'une clôture erronée.
5. L'articulation avec l'effacement RGPD (déjà ciblé sur `Bail`, bientôt `Locataire` post Sprint C
   EP-15).

## Décisions proposées — kickoff requis (aucune n'est tranchée par ce document)

> Contrairement à `ADR-16` (où les décisions D1-D8 avaient déjà été tranchées par le PO au moment
> de la rédaction, seul K1 restant ouvert), **aucune des décisions ci-dessous n'a encore de
> validation PO**. Chacune est présentée comme kickoff (`K1`→`K6`) avec une proposition par
> défaut, cohérente avec les patrons déjà adoptés sur ce projet, mais **rien n'est acquis avant
> tranchage explicite**.

### K1 — Déclenchement : manuel, jamais automatique

**Proposition** : la clôture est une action **manuelle** du bailleur (`POST
/api/biens/{bienId}/baux/{bailId}/cloture`), possible à tout moment — avant, à, ou après
`dateFin`. Aucun batch ne clôture automatiquement un bail à `dateFin` dépassée.

**Justification** : `dateFin` est contractuelle et peut être prolongée tacitement, renégociée, ou
au contraire le bail peut être résilié par anticipation — un automatisme clôturerait à tort des
baux reconduits sans mise à jour de `dateFin`. Cohérent avec le principe déjà appliqué à
l'archivage (Bien/Patrimoine/Gestionnaire/Locataire) : toujours une action bailleur explicite,
jamais un batch qui modifie un statut métier engageant.

### K2 — Date de clôture effective distincte de `dateFin`

**Proposition** : ajouter `bail.date_cloture_effective` (nullable, renseignée uniquement à la
clôture, jamais réécrite ensuite). `dateFin` garde sa valeur contractuelle d'origine, y compris si
la clôture réelle intervient à une autre date (résiliation anticipée ou tacite reconduction avant
clôture). Les deux dates peuvent légitimement diverger et sont conservées pour traçabilité/audit.

### K3 — Garantie non totalement restituée à la clôture : avertissement, pas de blocage

**Proposition** : la clôture n'est **pas bloquée** si `Garantie.statut ≠ RESTITUE_TOTAL` (ou
qu'aucune garantie n'existe) — l'API renvoie un avertissement explicite dans la réponse 200
plutôt qu'un rejet 409. La restitution de garantie reste une action indépendante
(`GarantieService.restituer`), non couplée techniquement à la clôture du bail.

**Alternative écartée par défaut** : bloquer (409) tant que la garantie n'est pas
`RESTITUE_TOTAL`. Écartée par défaut par cohérence avec la doctrine « avertir sans bloquer » déjà
retenue pour les doublons (BF-100, EP-15) — **mais à confirmer explicitement**, car il s'agit ici
d'une obligation légale/financière envers le locataire sortant, contrairement à un simple doublon
probable. Un bailleur pourrait vouloir un garde-fou dur ici plutôt qu'ailleurs.

### K4 — Impayés à la clôture : avertissement, pas de blocage

**Proposition** : de même, la présence de paiements `IMPAYE`/`EN_RETARD`/`PARTIEL` sur le bail ne
bloque pas la clôture — avertissement uniquement. Un bailleur doit pouvoir clôturer un bail dont
le recouvrement du solde se poursuit par ailleurs (ce n'est pas la clôture du bail qui éteint une
créance).

### K5 — Réouverture possible (symétrie avec la restauration déjà pratiquée)

**Proposition** : une clôture erronée peut être annulée (`Bail.statut : CLOS → ACTIF`) via un
endpoint dédié de réouverture, sous réserve qu'aucun autre bail `ACTIF` n'existe déjà sur le même
bien (contrainte `uq_bail_actif` inchangée, EF-12). Symétrique aux mécanismes de restauration déjà
en place (Bien, Patrimoine, Gestionnaire, Locataire) — cohérence de patron d'ensemble du produit.

### K6 — Purge de l'échéancier futur au-delà de la clôture

**Proposition** : à la clôture, les paiements déjà générés par le batch mensuel idempotent (US-30)
au statut `A_VENIR` et de période strictement postérieure à `date_cloture_effective` sont
**supprimés** (jamais les paiements déjà `RECU`/`PARTIEL`/`EN_RETARD`/`IMPAYE`, qui sont des faits
historiques immuables et jamais retouchés). Objectif : éviter que le batch `EN_RETARD` (US-31)
ne transforme un jour ces échéances fantômes en impayés sur un bail déjà clos.

**Alternative écartée par défaut** : conserver ces échéances telles quelles. Écartée par défaut
car elles polluent la vue impayés/alertes sans jamais devenir exigibles réellement — **mais à
confirmer**, certains bailleurs pourraient vouloir garder une trace de ce qui était initialement
prévu.

## Alternatives écartées

| Alternative | Raison de l'écart |
|---|---|
| Clôture automatique par batch sur `dateFin` dépassée | Risque de clôturer à tort des baux tacitement reconduits ou renégociés ; incohérent avec le principe « toute transition de statut métier engageant est une action bailleur explicite » déjà pratiqué sur ce projet |
| Développer `ClotureBailService` orchestrant garantie + paiements en une seule transaction (anticipé par ADR-14 §Extensions futures) | Hors périmètre de ce cadrage — les modules Garantie et Paiements restent volontairement découplés du cycle de vie du `Bail` (K3/K4), aucune orchestration transactionnelle unique n'est nécessaire dans cette proposition |
| Réutiliser `dateFin` comme date de clôture effective (pas de nouveau champ) | Écraserait la valeur contractuelle d'origine, perdant la traçabilité en cas de résiliation anticipée ou de reconduction tacite (K2) |

## Conséquences

- ✅ Débloque une fonctionnalité de cycle de vie aujourd'hui manquante et déjà bloquante en
  Production (impossibilité de créer un second bail sur un bien déjà loué une fois).
- ✅ Ne recouple pas Garantie/Paiements au `Bail` — préserve l'indépendance déjà en place.
- ✅ Cohérent avec les patrons d'archivage/restauration déjà pratiqués (Bien, Patrimoine,
  Gestionnaire, Locataire).
- ⚠️ Introduit un nouveau champ (`date_cloture_effective`) et un nouvel endpoint de réouverture —
  migration additive uniquement, aucune suppression de colonne (contrairement à V25/Sprint C
  EP-15).
- ⚠️ Six décisions kickoff (K1→K6) restent entièrement à la main du PO — ce document ne les tranche
  pas.

## Impacts sécurité

RBAC/ReBAC inchangés : clôture/réouverture réservées au rôle `BAILLEUR` sur ses propres biens
(même périmètre que la création de bail, EF-12/ADR-01). Aucune nouvelle fonction
`SECURITY DEFINER` nécessaire — la clôture ne traverse pas de frontière cross-tenant, contrairement
au statut Gestionnaire (ADR-16 D4).

## Impacts RGPD (ADR-03/ADR-16 D6)

Aucun changement du périmètre d'effacement — celui-ci reste piloté par `RgpdService`
(actuellement ciblé sur `Bail.anonymiserLocataire()`, migré vers `Locataire` par le Sprint C EP-15,
indépendamment de ce cadrage). Un bail `CLOS` reste effaçable au même titre qu'un bail `ACTIF`.

## Impacts performances

Négligeables : un champ nullable supplémentaire, une suppression ciblée de lignes `A_VENIR` par
requête indexée (`bail_id`, `statut`, `periode`), pas de nouvelle jointure sur les parcours
existants.

## Registre des risques (RSV-EP13)

| # | Risque | Mitigation proposée | Statut |
|---|--------|----------------------|--------|
| RSV-EP13-01 | Clôture prononcée par erreur sans que le bailleur ait restitué la garantie ou soldé les impayés (K3/K4 non bloquants) | Avertissement explicite dans la réponse API + affichage frontend dédié ; réouverture possible (K5) | Ouvert — dépend du tranchage K3/K4 |
| RSV-EP13-02 | Purge d'échéances `A_VENIR` (K6) supprimant par erreur une échéance qu'un bailleur souhaitait conserver en trace | Limitée aux échéances strictement futures et non exigibles (`A_VENIR` uniquement) ; jamais de suppression d'un fait déjà survenu | Ouvert — dépend du tranchage K6 |
| RSV-EP13-03 | Réouverture (K5) en conflit avec un nouveau bail déjà créé entre-temps sur le même bien | Contrainte `uq_bail_actif` déjà en base — la réouverture échoue en 409 si un bail `ACTIF` existe déjà | Mitigé par construction |
| RSV-EP13-04 | Interaction avec le Sprint C EP-15 (bascule `Bail.locataireId`) si les deux lots sont développés en parallèle | Aucune dépendance technique directe (K1→K6 n'exploitent pas `locataireId`) ; séquencement Production à confirmer au Plan d'Exécution | Ouvert — point de coordination, non bloquant |

## Points à trancher au kickoff (PO) — aucune décision implicite

| # | Question | Proposition par défaut |
|---|----------|--------------------------|
| K1 | Déclenchement manuel uniquement, jamais de batch automatique sur `dateFin` ? | Oui |
| K2 | Ajouter `date_cloture_effective` distincte de `dateFin` ? | Oui |
| K3 | Garantie non restituée à la clôture : avertissement ou blocage (409) ? | Avertissement |
| K4 | Impayés à la clôture : avertissement ou blocage (409) ? | Avertissement |
| K5 | Réouverture d'un bail clos par erreur : autorisée ? | Oui, sous réserve `uq_bail_actif` |
| K6 | Purge des échéances `A_VENIR` futures au-delà de la clôture ? | Oui |

**Aucun codage, migration ou Plan d'Exécution GO n'est autorisé avant que le PO ait tranché K1→K6.**

## Compatibilité et migration

Migration additive uniquement : `bail.date_cloture_effective` (nullable), aucune suppression de
colonne, aucun changement de contrat HTTP existant hors ajout de deux nouveaux endpoints
(clôture, réouverture) et d'un champ optionnel dans les réponses `BailDto`. Rollback applicatif
simple (contrairement à V25/Sprint C EP-15). Aucune dépendance bloquante avec le Sprint C EP-15 ;
le séquencement Production relatif des deux lots reste à arbitrer si les deux sont conduits en
parallèle (RSV-EP13-04).
