Launcher Civic (Honda Civic G9) - projeto Android

1. Crie um repositorio no GitHub e envie TODO o conteudo desta pasta (inclusive a pasta .github).
2. Abra a aba Actions: o build "Build APK" roda sozinho (uns 5 minutos).
3. Quando ficar verde, abra a execucao e baixe o arquivo "launcher-civic-apk" (Artifacts). Dentro tem o app-debug.apk.
4. Copie o APK para um pendrive e instale na central.
5. Aperte Home na central, escolha "Launcher Civic" e marque "Sempre".

Onde editar:
- Visual (os dois temas): app/src/main/assets/index.html
- Atalhos dos apps e pacotes: app/src/main/java/com/civic/launcher/MainActivity.java (metodo openName)
