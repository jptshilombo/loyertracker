# ADR-10 — Intégration IdP pour la création de compte gestionnaire (US-12)

| Champ | Valeur |
|-------|--------|
| Statut | Acceptée |
| Date | 2026-06-07 |
| Phase | 07 — Développement (Sprint S01) |
| Décidée par | PO jptshilombo@gmail.com (jordan) |
| Exigences couvertes | EF-04/05, ENF-01 ; risque backlog « couplage Keycloak Admin API » |
| Stories impactées | US-12 |

## Contexte

L'acceptation d'une invitation (US-12) doit créer — ou **réutiliser** si le compte existe déjà
(gestionnaire multi-bailleur, EF-05) — un utilisateur `GESTIONNAIRE` dans Keycloak via l'**Admin
API**. Le registre de risques (backlog §6) impose d'**encapsuler ce couplage IdP dans un adaptateur
testable**. Deux axes de décision : (1) mécanisme d'appel (lib dédiée vs REST) ; (2) stratégie de
test (Keycloak réel en CI vs double de test).

## Décision

**Architecture hexagonale.** Un **port** de domaine `GestionnaireIdentityProvider` (paquet
`comptes`) exprime le besoin métier, indépendant de l'IdP :

```java
interface GestionnaireIdentityProvider {
    GestionnaireIdentity creerOuRecuperer(String email, String nom, String prenom, String motDePasse);
}
```

- **Adaptateur de production** `KeycloakGestionnaireIdentityProvider` : appelle l'Admin API Keycloak
  via **Spring `RestClient`** (déjà fourni par `spring-web`, **aucune dépendance ajoutée** — pas de
  surface SCA/CVE supplémentaire ni de couplage de version à `keycloak-admin-client`). Flux :
  jeton `client_credentials` → recherche par e-mail (`exact=true`) → création + mot de passe +
  rôle `GESTIONNAIRE`, ou réutilisation si l'utilisateur existe.
- **Test** : la logique d'acceptation (validation du token, usage unique, création vs réutilisation)
  est vérifiée via un **faux port en mémoire**, sur PostgreSQL réel (Testcontainers). **CI légère.**

**Validation live de l'IdP déférée à R6** (réserve déjà actée au Gate 6 : « valider le flux
OIDC/PKCE en runtime contre le realm »). L'adaptateur n'ouvre aucune connexion au démarrage : sa
présence ne fragilise donc ni les tests ni le boot hors Keycloak.

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| **`org.keycloak:keycloak-admin-client`** | API typée plus confortable, mais dépendance lourde (resteasy/jakarta) à suivre au gate SCA et **couplée à la version Keycloak**. Disproportionné pour quelques appels REST. |
| **Testcontainers Keycloak en CI** | Validation live fidèle mais **+30–60 s/run** et image ~500 Mo ; redondant avec R6. Réévaluable si la fiabilité de l'adaptateur l'exige. |
| **Appels Keycloak directs dans le service** | Couple le domaine à l'IdP, non testable sans réseau. Contraire au registre de risques. |

## Conséquences

- ✅ Domaine découplé de l'IdP, **logique d'acceptation testable** sans réseau.
- ✅ Zéro dépendance ajoutée ; surface SCA inchangée.
- ✅ Réutilisation multi-bailleur (EF-05) portée par le port (`creerOuRecuperer`).
- ⚠️ L'adaptateur Keycloak n'est **pas couvert par un test live** : sa validation runtime est **R6**
  (avant mise en production). Risque accepté et tracé.
- ⚠️ Config requise hors dépôt : `KEYCLOAK_API_CLIENT_SECRET` (client confidentiel `loyertracker-api`
  avec droits `manage-users` sur le realm).
