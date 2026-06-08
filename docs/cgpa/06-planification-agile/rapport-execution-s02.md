# Rapport d execution S02 — US-20 a US-24

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Cadre | CGPA v3.0.1 |
| Phase | Phase 7 — Developpement |
| Sprint | S02 — Biens, baux, affectations |
| Date | 2026-06-07 |
| Statut | Approuve backend — GO sous reserve |

## 1. Perimetre approuve

Execution du Plan S02 pour US-20 a US-24, sans extension vers paiements, garanties, honoraires, batch ou dashboards. Le frontend metier S02 n a pas ete implemente dans cette execution.

## 2. Livrables produits

* Backend biens : entite, repository, service, DTO, requete et endpoints de liste, creation, modification et archivage.
* Backend baux : entite, repository, service, DTO, requete et endpoints de creation de bail et consultation d historique par bien.
* Backend affectations : entite, repository, service, DTO, requete et endpoints de creation, revocation et historique par bien.
* Migration Flyway V4 : statut `ARCHIVE`, resolution du bailleur par bien, fonction de liste des biens affectes a un gestionnaire.
* Tests d integration S02 : scenarios biens, unicite du bail actif, affectation active unique, rotation gestionnaire, acces gestionnaire actif/revoque.
* Mise a jour du Project State CGPA.

## 3. User stories

| Story | Resultat | Observations |
|-------|----------|--------------|
| US-20 | Realisee backend | CRUD biens et archivage ; filtrage bailleur explicite en plus de la RLS. |
| US-21 | Realisee backend | Creation de bail sur bien libre ; conflit 409 si bail actif deja present. |
| US-22 | Realisee backend | Historique des baux expose par bien. |
| US-23 | Realisee backend | Creation d affectation active avec honoraires ; conflit 409 si affectation active existante. |
| US-24 | Realisee backend | Revocation, rotation vers nouveau gestionnaire, historique conserve. |

## 4. Qualite et securite

* Autorisations : `@PreAuthorize` conserve sur les endpoints sensibles ; ReBAC reutilise pour acces bien bailleur/gestionnaire.
* Cloisonnement : RLS conservee ; ajout d un filtrage applicatif explicite par `bailleur_id` pour neutraliser les roles SQL privilegies en test ou runtime mal configure.
* Invariants base : unicite partielle des baux actifs et affectations actives conservee et testee.
* Gestion d erreurs : violations d unicite exposees en 409 pour les cas metier.

## 5. Verification

| Commande | Resultat |
|----------|----------|
| `mvn test -Dtest=S02BiensBauxAffectationsIntegrationTest` | OK — 3 tests S02, 0 echec |
| `mvn test` | OK — 35 tests, 0 echec |
| `mvn verify` | OK — tests, packaging, Spotless, JaCoCo |

## 6. Ecarts et limites

* Frontend S02 non implemente : les fonctionnalites sont disponibles cote API/backend uniquement.
* OpenAPI non genere : contrat API a formaliser avant recette large.
* Validation runtime Keycloak Admin API R6 toujours ouverte.
* Les tests utilisent un role PostgreSQL privilegie via Testcontainers ; le filtrage applicatif explicite reduit ce risque, mais une verification avec role applicatif non superuser reste souhaitable avant staging.

## 7. Decision proposee

**GO sous reserve** pour accepter S02 backend.

Decision approuvee le 2026-06-07.

Reserves : valider l arbitrage frontend S02 versus S03, documenter le contrat API, et traiter R6 avant toute ambition staging/prod.

## 8. Prochaine etape recommandee

Obtenir approbation PO/CGPA sur ce rapport, puis produire un Plan d Execution pour une seule des options suivantes :

1. Frontend S02 minimal ;
2. validation runtime R6 ;
3. S03 paiements et garanties.
