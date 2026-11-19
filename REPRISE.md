# NathBot — résumé + prompt pour une nouvelle session Claude

## 1. Résumé de ce qui est fait

- **Mod** : NathBot, mod **Forge 1.20.1** (Forge 47.x, Java 17), côté client, écrit de zéro **sans Baritone**.
- **Code** : dépôt GitHub `Nathou123058/bot-forge`, branche **`nathbot`** (la branche principale n'a pas été touchée).
- **Compilation** : GitHub Actions compile à chaque push (`.github/workflows/build.yml`) et publie le `.jar` :
  https://github.com/Nathou123058/bot-forge/releases/download/nathbot-latest/nathbot-1.20.1-forge-1.0.0.jar
- **Pas encore fait** : copier le `.jar` dans le dossier `mods` de l'instance CurseForge **« Nath et Vavan »**
  (la session cloud n'a pas pu accéder au PC `DESKTOP-D3CM4U6`).
- **Pas encore testé en jeu** : le mod compile, mais personne ne l'a lancé dans Minecraft.

### Contenu du mod
- Pathfinding A* maison (marcher, sauter, tomber, creuser, poser des ponts / piliers, éviter la lave).
- `/nb diamants 5` : fabrique les pioches si besoin, descend à Y=-58, creuse des galeries, mine et ramasse.
- `/nb finir` : enchaîne tout jusqu'au dragon de l'End (expérimental).
- 28 constructions prêtes + fichiers `.nbt` perso dans `.minecraft/nathbot/schematics` ; va chercher les matériaux manquants.
- Panneau en jeu sur la touche **P** (modifiable dans Options › Commandes › NathBot), K = stop, J = pause, C = zoom.
- Site web local **http://localhost:8765** (carte, inventaire, minage, constructions en 3D, fonctions, console, réglages).
- 48 fonctions activables, 42 commandes (`/nb aide` ou `#aide` dans le chat).

## 2. Prompt à coller dans une nouvelle session Claude Code **sur le PC**

> Ouvrir Claude Desktop → Claude Code → nouvelle session **locale** (sur ce PC, pas dans le cloud),
> dans le dossier de l'instance CurseForge ou dans `C:\Users\<toi>`. Puis coller :

```
Contexte : j'ai un mod Minecraft "NathBot" (Forge 1.20.1) déjà compilé sur GitHub.
Dépôt : https://github.com/Nathou123058/bot-forge (branche nathbot).
Fichier .jar : https://github.com/Nathou123058/bot-forge/releases/download/nathbot-latest/nathbot-1.20.1-forge-1.0.0.jar

Ce que je veux que tu fasses sur mon PC Windows, avec le terminal (PowerShell) :
1. Trouve le dossier de mon instance CurseForge appelée "Nath et Vavan".
   Regarde d'abord dans "$env:USERPROFILE\curseforge\minecraft\Instances\", puis cherche ailleurs
   (autres disques, Documents, AppData) si elle n'y est pas.
2. Vérifie dans le fichier minecraftinstance.json de l'instance que c'est bien Minecraft 1.20.1 avec Forge.
   Si ce n'est pas le cas, arrête-toi et dis-moi la version et le loader.
3. Télécharge le .jar ci-dessus dans le dossier "mods" de l'instance (crée-le s'il n'existe pas).
4. S'il y a un ancien "baritoneui*.jar" ou un autre Baritone dans "mods", déplace-le dans un dossier
   "mods_desactives" à côté (ne le supprime pas).
5. Montre-moi la liste finale des fichiers du dossier "mods".
6. Bonus si je te le demande : clone le dépôt (branche nathbot), compile avec ".\gradlew build"
   (il faut Java 17) et copie build\libs\nathbot-1.20.1-forge-1.0.0.jar dans "mods".

Ensuite explique-moi comment tester : lancer l'instance dans CurseForge, appuyer sur P en jeu,
taper "/nb aide", ouvrir http://localhost:8765. Si le jeu plante au lancement, lis le dernier
fichier dans le dossier "crash-reports" ou "logs\latest.log" de l'instance et dis-moi ce qui ne va pas.
```

## 3. Si tu veux plutôt garder une session cloud
Il faut partager le dossier de l'instance (pas tout le disque, pas `AppData`) dans
Claude Desktop → Settings → Claude Code → Remote Control, avec une appli Claude à jour.
Puis dire à la session : « installe NathBot dans mon instance ».
