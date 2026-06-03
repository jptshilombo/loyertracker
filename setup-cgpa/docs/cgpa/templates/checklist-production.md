# Checklist de production — {{NOM_DU_PROJET}}

| Champ | Valeur |
|-------|--------|
| Projet | {{NOM_DU_PROJET}} |
| Version à déployer | {{…}} |
| Date prévue | {{AAAA-MM-JJ}} |
| Phase | 10 — Production |
| Gate visé | Gate 8 |

> Cette checklist conditionne le **Gate 8**. Tout élément ⛔ non coché interdit le déploiement.

## 1. Pré-requis fonctionnels
- [ ] Recette prononcée (PV de recette validé) ⛔
- [ ] Aucune anomalie bloquante ouverte ⛔

## 2. Build & artefacts
- [ ] Build de production validé par la CI ⛔
- [ ] Image Docker versionnée et signée
- [ ] Configuration par environnement externalisée

## 3. Sécurité
- [ ] Aucun secret en clair ⛔
- [ ] Scans SAST/SCA/images sans vulnérabilité critique ⛔
- [ ] Authentification/autorisation (Keycloak) vérifiée
- [ ] Certificats / HTTPS en place

## 4. Données
- [ ] Plan de migration validé
- [ ] Sauvegarde réalisée avant déploiement ⛔
- [ ] Plan de restauration testé

## 5. Déploiement & réversibilité
- [ ] Stratégie de déploiement définie (rolling/blue-green/canary)
- [ ] Plan de rollback documenté et testé ⛔
- [ ] Fenêtre de déploiement planifiée

## 6. Supervision
- [ ] Logs centralisés opérationnels
- [ ] Monitoring & alerting actifs ⛔
- [ ] Indicateurs post-déploiement définis

## 7. Décision Gate 8
- Décision : ☐ Go ☐ Go sous réserve ☐ No Go
- Réserves / actions : {{…}}
- Date & responsable : {{…}}
