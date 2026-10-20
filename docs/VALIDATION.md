# Versão debug estabilizada — Pool Aim Assist

Data: 20/10/2026. Revisão do projeto existente para entrega de APK de teste. Sem publicação na Play Store ou assinatura release.

## Entrega local

- Arquivo instalável: `dist/Pool-Aim-Assist-debug.apk` (16,319,199 bytes).
- Pacote `com.poolaim.app`, versão 0.1.0 (versionCode 1), minSdk 26, target/compileSdk 35.
- APK universal com ARM64-v8a (Galaxy Tab S9 Plus), armeabi-v7a, x86 e x86_64.
- Assinatura debug v2 validada por apksigner; instalado com sucesso no emulador.
- `dist/SHA256SUMS` e `dist/LEIA-ME.md` acompanham o APK. Instruções também em [INSTALLATION.md](INSTALLATION.md).

SHA-256 do APK local:

```text
37d939dd0a53f6f7dc288cfe7b57bb36cf2708f49d258728468451808db5cc48
```

## Compilação e testes desta revisão

```sh
./gradlew --no-daemon :physics:test :vision-api:test :recognition:test :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

**BUILD SUCCESSFUL, 1 min 4 s.** Lint: **0 erros e 1 aviso**, relativo à orientação paisagem solicitada.

**57 testes JVM passaram**, zero falhas/erros/ignorados: 30 de geometria/reflexões, 11 de frames/pipeline/buffers, 2 novos de privacidade/encerramento, 12 de reconhecimento/integração e 2 do diagnóstico de pixels.

**6 testes instrumentados passaram juntos: OK (6 tests), 151,443 s**, no Android 9/API 28 por software, sem KVM:

1. Captura com consentimento real, frames, resolução/densidade, rotação, parada, notificação removida, threads capture-images/vision-latest encerradas e nova autorização cancelada.
2. Abertura, simulador e calibração existentes.
3. Sobreposição: passagem de toques, arrasto, menu, mostrar/ocultar linhas, rotação, Home, retorno às configurações e remoção das janelas/notificação/tarefa.
4. Canvas não desenha previsões vencidas, ocultas ou de resolução diferente.
5. Alinhamento de pixels com deslocamento da janela; preservação da bola e mira original contra as linhas/reflexões.
6. Fluxo real da mesa Canvas: MediaProjection → identificação de três bolas e mira → motor → onDraw da janela; mudança da direção visual por toque no slider, pausa, direção manual, quadro congelado e parada.

Instalação e instrumentação executadas diretamente:

```sh
adb install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk
adb install -r --no-streaming app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.poolaim.app.test/androidx.test.runner.AndroidJUnitRunner
```

O conteúdo disponível para visão consiste em fixtures sintéticas e na mesa renderizada pelo próprio aplicativo, capturada pela API real. Nenhuma imagem real do 8 Ball Pool foi fornecida/validada. O teste do desenho usa o onDraw da janela real e testes de pixels de Canvas; não se afirma comparação por screenshot físico sobre o jogo.

## Correções de estabilidade e privacidade

- Parar a análise ou iniciar outra autorização descarta a miniatura global e o quadro congelado de calibração. Resultados em processamento de uma revisão anterior não podem recolocá-los.
- O processador mantém somente geometria para a comparação entre frames, sem reter a imagem anterior após encerrar o pipeline.
- A troca do ImageReader transfere a propriedade somente após anexar a nova superfície. Em caso de falha, o leitor anterior continua alcançável pela limpeza do serviço.
- Backup/transferência foram explicitamente excluídos por regras Android 12+ e backup legado desativado; tráfego HTTP em texto claro foi bloqueado.
- O APK não solicita INTERNET, câmera, microfone, localização ou armazenamento. Usa SYSTEM_ALERT_WINDOW, POST_NOTIFICATIONS, FOREGROUND_SERVICE e tipos mediaProjection/specialUse. As permissões do Android são solicitadas por suas interfaces normais.
- Serviços não exportados, PendingIntents imutáveis, receptor de encerramento não exportado, projeção não persistida, uma autorização por sessão e um VirtualDisplay redimensionado. Não existe reinício sticky/boot nem bypass de conteúdo protegido.
- Buffers limitados e descarte de frames antigos foram mantidos, assim como a supressão de linhas vencidas após 700 ms.

Uma tela de edição já aberta pode manter sua própria cópia do quadro até sair dela; as referências globais são liberadas ao parar. Preferências e geometria/cor de calibração ficam no armazenamento privado; não há gravação ou envio de frames em produção.

## GitHub Actions

Workflow: `.github/workflows/android.yml`, nome **Android Debug**. Eventos: push, pull_request e workflow_dispatch. O job build executa os testes JVM, assembleDebug, lint, prepara checksum/guia e publica o artefato **Pool-Aim-Assist-debug** por 14 dias. O job device-tests é separado e executa connectedDebugAndroidTest em API 35 com KVM; relatórios também são publicados.

Localização após uma execução real bem-sucedida do build:

**OtavioRezende-site/pool-aim-assist → Actions → Android Debug → execução → Summary → Artifacts → Pool-Aim-Assist-debug**.

O ZIP contém `Pool-Aim-Assist-debug.apk`, `SHA256SUMS` e `LEIA-ME.md`. APKs do CI podem usar outra chave debug; consulte as instruções de instalação. A configuração local do YAML não comprova publicação remota. Execução remota concluída com sucesso: [Android Debug #6](https://github.com/OtavioRezende-site/pool-aim-assist/actions/runs/39448424388), commit `f7c3453`.

- Job `build`: aprovado em 2 min 26 s; testes JVM, assembleDebug e lint concluídos, APK publicado em **Pool-Aim-Assist-debug**.
- Job `device-tests`: aprovado em 4 min 2 s; suíte de 6 testes instrumentados no Android 15/API 35, incluindo captura com consentimento, mudança de direção visual, rotação, sobreposição e encerramento.
- Artefatos publicados: **Pool-Aim-Assist-debug**, **Android-build-reports** e **Android-device-test-reports**.
- O runner foi fixado em Ubuntu 24.04, o caminho do SDK resolvido explicitamente e setup-java atualizado para v5. O script `.github/scripts/report-device-tests.py` expõe falhas da instrumentação nas anotações do job.
- A primeira execução dos testes API 35 revelou uma premissa incorreta no teste de sobreposição: o bloqueio artificial de orientação persistia ao abrir Configurações. O teste agora libera o bloqueio após a navegação, antes de verificar o retorno à paisagem. A execução final passou sem alterar a orientação do aplicativo ou contornar regras do sistema.
- Evidência dos jobs/etapas: [validation/stable/github-actions.json](validation/stable/github-actions.json). APKs gerados no CI têm seu próprio checksum dentro do ZIP; o SHA acima identifica somente o pacote local.

As alterações posteriores a essa execução são somente de documentação, com CI omitido por `[skip ci]`.

## Limites de validação

**Ainda não validado diretamente sobre o 8 Ball Pool ou no Galaxy Tab S9 Plus físico/One UI.** Android 9/API 28 foi validado localmente e Android 15/API 35 foi validado pelo CI com KVM. Android 14/API 34 não foi executado separadamente. Os ANRs de um emulador API 35 anterior sem KVM não impediram a validação da execução remota final.

A detecção automática é experimental para pano verde/azul, círculos e mira clara. Skins, textura, bolas encostadas/ocultas, perspectiva e mudanças visuais podem exigir calibração e direção manuais. Os testes de modelo manual passaram, mas não automatizam todos os gestos do editor. O escore de qualidade é heurístico, não uma probabilidade calibrada.

O aplicativo não automatiza tacadas, acessa memória do jogo, altera arquivos de terceiros nem contorna bloqueios. Trajetórias são estimativas geométricas sem força, atrito, efeito ou garantia de acerto. Recalibre após movimentos das bolas. O teto de captura é 15 FPS, sem garantia de frequência no dispositivo.

Logs e auditoria desta revisão: [validation/stable/](validation/stable/). Relatórios de etapas anteriores permanecem nas demais pastas de validation como histórico.
