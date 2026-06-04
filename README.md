# LoyerTracker

Application web de **gestion locative bailleur-centrée avec délégation fine par bien** : centralisation des baux, suivi des paiements de loyer mois par mois (à terme échu), gestion des garanties locatives, honoraires de gestion délégués, et alertes sur les échéances clés (loyers en retard, fin de bail, préavis, garanties non restituées).

## État du projet

Projet gouverné par le **CGPA** (Cadre de Gouvernance des Projets Applicatifs, `setup-cgpa/`). Phases de gouvernance franchies :

| Gate | Phase | Statut |
|------|-------|--------|
| Gate 0 | Idée / opportunité | ✅ Go |
| Gate 1 | Expression du besoin (EB v1.2) | ✅ Go |
| Gate 2 | Faisabilité | ✅ Go |
| Gate 3 | Cahier des charges | ✅ Go |
| Gate 4 | Architecture & conception | ✅ Go — **verrou de codage levé** |
| Gate 5 | Backlog & planification | ✅ Go |
| Gate 6 | DevSecOps | 🚧 en cours (Phase 07) |

## Stack

Spring Boot (Java 21) · Angular · Keycloak (OIDC/PKCE) · PostgreSQL · Nginx (reverse proxy) · Docker · CI/CD.

## Démarrage rapide (dev)

> ⚠️ La stack n'est pas encore opérationnelle (en cours d'implémentation — Phase 07).

```bash
cp .env.example .env      # renseigner les secrets locaux
docker compose up         # nginx (443) · api · keycloak · postgres
```

## Documentation

| Sujet | Emplacement |
|-------|-------------|
| Architecture & ADR | `docs/cgpa/05-architecture-conception/` |
| Backlog & sprints | `docs/cgpa/06-planification-agile/` |
| Plan d'implémentation DevSecOps | `docs/cgpa/07-devsecops/plan-implementation.md` |
| Contribuer | `CONTRIBUTING.md` |
