# Checklist production (mise en exploitation)

> Checklist générale de la phase **10 — Production** / **Gate 8**. Voir aussi le gabarit opérationnel `templates/checklist-production.md`.

## 1. Pré-requis
- [ ] Recette prononcée (Gate 7 Go)
- [ ] Aucune anomalie bloquante
- [ ] Build de production validé en CI

## 2. Sécurité
- [ ] Aucun secret en clair
- [ ] Scans sécurité sans vulnérabilité critique
- [ ] HTTPS/TLS et certificats en place
- [ ] Authentification (Keycloak) vérifiée en prod

## 3. Données
- [ ] Sauvegarde réalisée avant déploiement
- [ ] Plan de migration validé
- [ ] Restauration testée

## 4. Déploiement
- [ ] Stratégie de déploiement définie
- [ ] Plan de rollback documenté et testé
- [ ] Fenêtre et communication planifiées

## 5. Supervision
- [ ] Logs centralisés actifs
- [ ] Monitoring & alerting opérationnels
- [ ] Indicateurs post-déploiement définis

## 6. Post-déploiement
- [ ] Vérifications fonctionnelles critiques
- [ ] Rapport de mise en production rédigé
- [ ] Décision Gate 8 consignée
