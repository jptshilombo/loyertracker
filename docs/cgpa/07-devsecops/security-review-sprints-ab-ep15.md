# Revue sécurité dédiée — Sprints A+B (EP-15, Gestion des personnes)

| Champ | Valeur |
|---|---|
| Date | 2026-07-15 |
| Candidat | merge PR #209 `4d8a760` (+ PR #217 documentaire `c9200a5`), tag `sha-c9200a51` |
| Périmètre | Cycle de vie du Gestionnaire (statut global, activation Keycloak, garde cross-tenant d'archivage) ; nouvelle entité Locataire (RLS standard) |
| Motif | `docs/addendum-personnes.md` §Impacts exige une revue sécurité dédiée avant Gate Staging du Sprint A — nouveau patron cross-tenant (fonctions `SECURITY DEFINER` traversant la RLS d'`affectation`) |
| Verdict | **PASS — aucune réserve bloquante** |

## Contrôles

| Contrôle | Résultat | Preuve |
|---|---|---|
| RLS `Locataire` | PASS | Table `locataire` (V24) `ENABLE`+`FORCE ROW LEVEL SECURITY`, policy `bailleur_isolation` identique au pattern V1/V12/V20/V22 ; `isolationRlsCrossBailleur` (`LocataireIntegrationTest`) prouve 404 cross-tenant en lecture/écriture et 0 fuite en liste |
| `Gestionnaire` reste hors RLS (inchangé, ADR-01) | PASS | Aucune colonne `bailleur_id` ajoutée à `gestionnaire` (V23) ; portée globale assumée et documentée (ADR-16 D1, RSV-EP15-01 acceptée PO) |
| Fonctions `SECURITY DEFINER` à surface minimale | PASS | `gestionnaire_a_affectation_active(uuid)` et `gestionnaire_a_relation(uuid,uuid)` (V23) : retournent uniquement un `boolean`, `SET search_path = public`, `OWNER TO loyertracker_batch`, `EXECUTE` accordé au seul rôle `loyertracker_api` — même patron que `gestionnaire_affecte_actif` (V13) et les fonctions publiques de quittance (V22) |
| Injection SQL | PASS | Les deux fonctions et leurs appels côté service (`AuthorizationService`, `GestionnaireService`) utilisent exclusivement des paramètres liés (`:g`, `:o`, `CAST(... AS uuid)`), aucune concaténation |
| RBAC / ReBAC Gestionnaire (RM-107) | PASS | `@PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")` sur chaque endpoint mutateur/consultatif ; `peutAccederGestionnaire` fail-closed (retourne `false` sur JWT absent, rôle non-BAILLEUR ou pas de relation) ; test dédié `rbacGestionnaireNAdministrePasEtBailleurSansRelationRefuse` prouve qu'un Gestionnaire n'administre jamais un autre Gestionnaire et qu'un bailleur sans relation est refusé (403) |
| RBAC Locataire | PASS | `@PreAuthorize("hasRole('BAILLEUR')")` au niveau contrôleur, cloisonnement supplémentaire porté entièrement par la RLS (aucun ReBAC nécessaire, cohérent avec l'absence de portée cross-bailleur) ; `rbacGestionnaireRefuse` confirme le rejet 403 du rôle `GESTIONNAIRE` |
| Garde cross-tenant d'archivage (D4) | PASS | `archivageBloqueParAffectationActiveChezUnAutreBailleurPuisAutorise` prouve que l'archivage reste bloqué tant qu'une affectation `ACTIVE` existe chez un **autre** bailleur (invisible sous RLS normale), révélée uniquement par la fonction `SECURITY DEFINER`, puis autorisé une fois cette affectation levée |
| Activation Keycloak pilotée par l'app | PASS | `KeycloakGestionnaireIdentityProvider.definirActivation` isole les erreurs réseau (`ResponseStatusException` 502), aucune fuite de jeton admin, appel systématiquement postérieur à la mutation en mémoire — cohérence transactionnelle : un échec Keycloak fait échouer la transaction DB (rollback), un succès Keycloak suivi d'un échec applicatif ultérieur reste un risque résiduel mineur déjà connu de ce patron (best-effort, non bloquant) |
| Exposition HTTP | PASS | `/api/gestionnaires/**` et `/api/locataires/**` absents de tout `permitAll` dans `SecurityConfig` — retombent sous `.anyRequest().authenticated()`, JWT requis en amont du `@PreAuthorize` |
| Audit | PASS | Points `MODIFIER_GESTIONNAIRE`/`SUSPENDRE_GESTIONNAIRE`/`REACTIVER_GESTIONNAIRE`/`ARCHIVER_GESTIONNAIRE`/`RESTAURER_GESTIONNAIRE` et `CREER_LOCATAIRE`/`MODIFIER_LOCATAIRE`/`ARCHIVER_LOCATAIRE`/`RESTAURER_LOCATAIRE` journalisés systématiquement ; historique Gestionnaire (affectations + audit) prouvé sans fuite cross-bailleur (`historiqueSansFuiteCrossBailleur`) |
| CI sécurité | PASS | Gitleaks, SCA/Trivy dépendances et images API/Web, CodeQL Java/Kotlin+JS/TS verts sur `4d8a760` et `c9200a5` (7/7 checks) |

## Défauts détectés en revue de code et corrigés avant ce Gate

Deux défauts de correction (non sécurité au sens strict, mais avec un angle données/traçabilité)
ont été détectés lors de la revue du PR #209 et corrigés avant merge (commit `66a57dc`, cf.
`docs/project-state.md`) :

1. `GestionnaireService.modifierProfil` effaçait silencieusement la photo (champ **partagé entre
   bailleurs**, ADR-16 D1) dès qu'une mise à jour partielle omettait `photoBase64` — corrigé par
   un garde-fou (photo conservée si le champ est absent, effacée seulement si transmise vide),
   couvert par le test `photoConserveeLorsDUneModificationPartielleDuProfil`.
2. `LocataireService.creer` renvoyait `dateCreation: null` à la création (colonne `insertable=false`
   jamais relue après un `merge()` implicite de Spring Data JPA) — corrigé par réassignation de la
   référence + `em.refresh()`, couvert par une assertion dédiée dans `LocataireIntegrationTest`.

Aucun des deux n'ouvrait de faille d'accès cross-tenant ni de contournement RBAC/RLS ; retenus ici
pour traçabilité complète avant Gate.

## Observation non bloquante

`GestionnaireProfilRequest.photoBase64` et `LocataireRequest.photoBase64` n'ont pas de contrainte
`@Size` explicite (contrairement à `telephone`/`observations`), et aucune limite `server.tomcat.*`
dédiée à la taille du corps HTTP n'est configurée dans `application.yml`. Même précédent que
`quittance.pdf` (ADR-15, sans incident connu) : accepté pour ce Gate, à surveiller si la
volumétrie des photos devient un usage réel (durcissement futur possible, non requis ici).

## Décision

Le risque « nouveau patron cross-tenant » (RSV-EP15-01, fonctions `SECURITY DEFINER`) identifié au
cadrage EP-15 est traité : surface minimale, paramètres liés, `EXECUTE` restreint, comportement
prouvé par test dédié en conditions cross-tenant réelles. La revue sécurité autorise le Gate
Staging Sprints A+B ; elle n'autorise aucune promotion Production.
