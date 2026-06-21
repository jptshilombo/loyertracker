# ADR-11 — Introduction de la notion de Patrimoine

| Champ | Valeur |
|-------|--------|
| Code de décision | **D-PAT-001** |
| Statut | Proposée — en attente d'approbation PO (Plan d'Exécution non démarré, aucun code produit) |
| Date | 2026-06-21 |
| Phase | 07 — Développement (lot post-go-live, sous Phase 7 continue) |
| Exigences couvertes | EF-90→EF-96, ENF-90, RM-90→RM-99 (addendum CDC) |
| Documents liés | `docs/cgpa/02-expression-besoin/addendum-patrimoine.md`, `docs/cgpa/04-cahier-des-charges/addendum-patrimoine.md`, `docs/cgpa/06-planification-agile/addendum-patrimoine-backlog.md`, `docs/cgpa/06-planification-agile/plan-execution-patrimoine.md`, `docs/cgpa/07-devsecops/securite-patrimoine.md` |

## Contexte

Le modèle métier validé en Phase 01-05 (fiche idée Gate 0, EB Gate 1, DAT Gate 4) est strict à deux niveaux : `Bailleur (1) ──< (N) Bien (1) ──< (N) Bail`. Chaque bien est directement rattaché au bailleur ; chaque affectation de gestionnaire (ADR-01/ADR-02) cible un bien précis et un seul. Ce modèle convient à un portefeuille plat, mais ne permet pas de regrouper des biens en ensembles patrimoniaux homogènes (par exemple un immeuble, une zone géographique ou une société porteuse) ni de déléguer un gestionnaire sur un **groupe de biens** en une seule opération.

Le PO a validé une décision métier introduisant un niveau intermédiaire **Patrimoine** entre `Bailleur` et `Bien`, ainsi qu'une typologie administrable des biens et un mécanisme d'affectation à deux granularités (patrimoine et bien) avec règles de priorité et d'exception. Cette décision est portée par le backlog validé US-70→75 (numérotation source ; voir réserve de renumérotation en §Conséquences).

## Problème

1. Un bailleur avec un grand nombre de biens ne peut aujourd'hui les regrouper logiquement ; toute opération de délégation est unitaire (bien par bien), ce qui devient coûteux dès que le portefeuille grandit (cf. ENF-06, cible ≤ 50 biens, déjà identifiée comme limite de performance dans le DAT).
2. Le champ `Bien.type` existe déjà en base (`bien.type`, colonne `String` libre, voir `Bien.java`) mais n'est **pas contraint** : aucune liste administrable n'est imposée aujourd'hui, ce qui contredit la nouvelle règle métier #5.
3. Le modèle de cloisonnement (ADR-01) et d'autorisation fine (ADR-02) ne connaît que deux primitives : « bailleur propriétaire de tout son tenant » et « gestionnaire affecté activement à un bien ». Il n'existe ni notion d'affectation à un patrimoine, ni mécanique de priorité/exception entre les deux niveaux.
4. Aucune des phases validées (EB, CDC, DAT, backlog) n'anticipait ce niveau intermédiaire — contrairement au lot « Quittances de loyer » qui était déjà annoncé en roadmap (EB §2.2). Cette décision constitue donc une **extension de périmètre réelle**, non un simple déblocage d'un item déjà planifié.

## Décision

**Introduire `Patrimoine` comme nouvelle entité de premier niveau, intercalée entre `Bailleur` et `Bien`, en conservant la défense en profondeur existante (ADR-01) et la séparation AuthN/ReBAC existante (ADR-02).**

Principes retenus :

1. **Ownership inchangé en surface** : `Patrimoine` porte une colonne `bailleur_id` (cohérent avec la règle ADR-01 « toute table métier porte `bailleur_id` ») ; `Patrimoine (1) ──< (N) Bien` devient obligatoire — `Bien.patrimoine_id` non nul.
2. **Typologie administrable** : `Bien.type` cesse d'être une chaîne libre ; il référence une liste fermée mais administrable (`Appartement`, `Boutique`, `Bureau`, `Villa`, `Terrain`, `Entrepôt`, `Autre`), proposée comme table de référence plutôt qu'un `enum` Java figé, pour rester administrable sans déploiement (RM-94).
3. **Contrat = synonyme conceptuel de l'entité `Bail` existante** : la règle « un seul contrat ACTIF par bien » est déjà satisfaite par `Bail.statut` (`ACTIF`/`CLOS`) et l'index `uq_bail_actif` (EF-12, V1). Aucune nouvelle entité « Contrat » n'est créée ; le terme « Contrat » utilisé dans la décision métier est traité comme une **dénomination produit** de l'entité technique `Bail`, afin d'éviter un renommage de table/entité/API à risque et sans valeur ajoutée (cf. Alternatives écartées).
4. **Affectation à deux granularités** : `Affectation` gagne un attribut `patrimoine_id` optionnel, exclusif de `bien_id` (l'un des deux est renseigné, jamais les deux — contrainte `CHECK` conceptuelle). Une affectation « patrimoine » confère un accès hérité à tous les biens du patrimoine ; une affectation « bien » reste ponctuelle.
5. **Priorité et exceptions** : une affectation **bien** pour un couple (gestionnaire, bien) **prévaut** sur l'affectation **patrimoine** héritée pour ce même bien. Deux natures d'affectation bien sont distinguées par un attribut `type_exception` :
   - `INCLUSION` : accède à ce bien précis sans disposer d'affectation patrimoine (complément local) ;
   - `EXCLUSION` : retire l'accès à ce bien précis malgré une affectation patrimoine active (restriction locale).
   Le périmètre effectif d'un gestionnaire sur un patrimoine P = (biens de P si affectation patrimoine ACTIVE sur P) ∪ (biens avec affectation bien `INCLUSION` ACTIVE) − (biens avec affectation bien `EXCLUSION` ACTIVE pour ce même gestionnaire).
   > ⚠️ Cette mécanique de résolution est une **proposition d'interprétation** de la règle métier #9/#10 (le PO n'a pas précisé l'algorithme). À valider explicitement avant tout codage (cf. Risques).
6. **Cloisonnement inchangé** : la RLS PostgreSQL (ADR-01, couche 2) s'étend naturellement à `patrimoine` (même policy `bailleur_id`) ; `AuthorizationService` (ADR-02) gagne une résolution à deux niveaux mais conserve son rôle de source de vérité unique de l'autorisation fine.

## Conséquences

- ✅ Permet la délégation par lot (un gestionnaire ↔ un patrimoine entier) sans perdre la granularité fine existante (un gestionnaire ↔ un bien).
- ✅ Aucune rupture de la défense en profondeur ADR-01 (RLS) ni de la séparation IdP/ReBAC ADR-02 : extension, pas remplacement.
- ✅ `Bail`/EF-12 (contrat actif unique) déjà conforme : **aucun développement requis** sur cette règle, seulement une clarification terminologique.
- ⚠️ `AuthorizationService.peutAccederBien`/`estGestionnaireAffecteActif` (cf. `securite/AuthorizationService.java`) doivent être étendus pour résoudre l'héritage patrimoine → bien et les exceptions — zone de risque sécurité prioritaire, à couvrir par des tests d'autorisation dédiés (cf. `securite-patrimoine.md`).
- ⚠️ Migration de données pour les ~biens existants : un `Patrimoine` par défaut devra être créé pour chaque bailleur existant afin de satisfaire `Bien.patrimoine_id NOT NULL` sans rupture (à traiter au Plan d'Exécution Sprint 1, pas de migration SQL produite à ce stade).
- ⚠️ Renumérotation backlog nécessaire : **US-70/71/72 sont déjà occupées** par le backlog validé Gate 5 (RGPD export/effacement, suite de tests d'autorisation, durcissement SAST/Nginx — `product-backlog.md` EP-08). Les stories de la décision Patrimoine sont renumérotées **US-80→85** sous un nouvel **Epic EP-09** (voir `addendum-patrimoine-backlog.md`).
- ⚠️ Aucune phase déjà validée (Gate 0/1/3/4/5) n'est rejouée ni modifiée : cette ADR et ses addenda sont **additifs**, à l'image des reprises CGPA v5.0.1/v5.2 déjà pratiquées sur ce projet.

## Risques

| Risque | Niveau | Mitigation proposée |
|--------|--------|----------------------|
| Algorithme de résolution priorité/exception (héritage patrimoine, `INCLUSION`/`EXCLUSION`) non validé explicitement par le PO | Majeur | Faire trancher l'algorithme exact en ouverture de Sprint 2 (critère GO dédié) avant tout codage de `AuthorizationService` |
| Régression de cloisonnement pendant la transition (biens existants sans patrimoine) | Critique | Patrimoine par défaut auto-créé par bailleur à la migration ; tests de non-régression sur la suite d'autorisation existante (`SecurityIntegrationTest`) avant fusion |
| Confusion durable « Bail » (code/DB) vs « Contrat » (vocabulaire métier de la décision) | Mineur | Glossaire explicite dans l'addendum CDC ; aucun renommage de code |
| Collision d'identifiants US-70/71/72 si le backlog source de la décision est utilisé tel quel | Moyen gouvernance | Renumérotation actée US-80→85 dans cette ADR et dans l'addendum backlog |
| Performance : jointure supplémentaire `patrimoine` sur les dashboards (ENF-06, < 2 s / 50 biens) | Mineur | Index `bien(patrimoine_id)`, `affectation(patrimoine_id, statut)` à prévoir (proposés, non créés) |

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| Modéliser « Patrimoine » comme un simple tag/libellé sur `Bien` (sans entité ni table dédiée) | Ne permet pas l'affectation gestionnaire au niveau patrimoine (règle #8) ni le cloisonnement RLS dédié ; insuffisant pour les règles #1/#2/#3/#9/#10 |
| Renommer l'entité `Bail` en `Contrat` dans le code/la base | Coût et risque de renommage transverse (entité JPA, table, colonnes `bail_id`, endpoints `/api/biens/{id}/baux`, tests, documentation) sans aucune valeur fonctionnelle nouvelle — la règle visée (#6/#7) est déjà satisfaite par `Bail` |
| Affectation patrimoine = simple raccourci créant N affectations « bien » au moment de la création | Perd la traçabilité de l'intention (« ce gestionnaire gère ce patrimoine ») et complique la rotation/révocation en masse (un seul événement métier attendu, pas N) ; rompt avec l'esprit d'EF-23 (rotation sans perte d'historique) |
| Reprendre la numérotation source US-70→75 telle que fournie | Collision directe avec le backlog déjà validé Gate 5 (US-70/71/72 RGPD/sécurité) — aurait écrasé la traçabilité existante |
