# Pool Aim Assist

MVP Android nativo em Kotlin, Jetpack Compose/Material 3 e MVVM. Reutiliza MediaProjection/ImageReader, o motor geométrico Kotlin e o serviço WindowManager. Android mínimo 8/API 26, compile/target 35, orientação paisagem e coordenadas proporcionais. Preparado para validação no Galaxy Tab S9 Plus; esse dispositivo e o 8 Ball Pool real ainda precisam de testes.

## Fluxo funcional

1. Abra **Iniciar assistência visual → Iniciar**. Conceda sobreposição e confirme a captura no diálogo do Android. No Android 14+ a autorização solicita a tela inteira para alinhar as coordenadas; não há captura silenciosa nem reutilização de autorização.
2. Abra a mesa no outro aplicativo. Posicione **PA fora do pano**; se o controle encobrir a mesa, as previsões são suspensas.
3. O detector procura uma superfície retangular verde/azul, componentes circulares, uma única bola branca e uma linha de mira neutra/clara. Duas observações compatíveis confirmam a cena. A direção visual alimenta o motor geométrico sem automação de tacadas.
4. A sobreposição desenha a extensão da branca em magenta, a direção inicial do alvo em laranja e reflexões em azul, com até duas tabelas. Ela preserva os pixels da mira original e das bolas para reduzir realimentação da captura. As linhas não recebem toques.
5. Use PA para **Pausar/Retomar**, **Parar análise**, **Mostrar/Ocultar linhas**, ajustar direção manual, calibrar ou encerrar. Pausar preserva a sessão autorizada; parar encerra a projeção e exige novo consentimento para outra sessão.

A análise fica suspensa enquanto a interface principal do Pool Aim Assist está aberta, exceto na mesa de teste. Isso preserva o último quadro do jogo para calibração. Abrir o menu também suspende a interpretação para não analisar os próprios controles.

## Calibração e direção manual

No quadro desejado, abra **PA → Calibrar mesa**. O quadro anterior à abertura do menu é congelado. Toque em dois cantos internos da área jogável. **Salvar só limites** mantém bolas e direção automáticas. Para geometria manual, use **Marcar branca e bolas**, marque primeiro a branca e depois todas as outras bolas, ajuste o raio e confirme os dados antes de salvar. Os centros precisam estar dentro da mesa e os círculos não podem se sobrepor.

A geometria manual salva a assinatura média de cor de cada bola. A cada quadro, mudanças de cor nas posições marcadas ou mudanças de orientação/proporção invalidam a previsão. Isso não é rastreamento completo de bolas: **recalibre após tacadas ou mudanças do visual**. Pequenos deslocamentos e bolas de cores semelhantes podem exigir revisão manual. Perspectiva inclinada não é suportada.

Se a mira não for detectada, ative **Direção manual** e ajuste o ângulo na tela ou no menu flutuante: 0° aponta para a direita, 90° para baixo. O cálculo continua usando a captura atual e a geometria reconhecida/calibrada. A opacidade das linhas é ajustável e persistente. A janela mantém também o limite de transparência necessário para passagem de toques no Android 12+.

## Limites da identificação automática

O detector usa heurísticas de cor, forma e alinhamento, sem OpenCV ou modelo treinado específico da Miniclip. A qualidade exibida é um escore heurístico, não probabilidade calibrada. Pano com textura/cores diferentes, bolas encostadas ou parcialmente ocultas, reflexos, números/faixas, animações, caçapas, mesas inclinadas e linhas pouco visíveis podem impedir a identificação. A calibração manual é a alternativa funcional para esses casos.

Não se afirma reconhecimento robusto de todas as mesas/skins do 8 Ball Pool. As validações automatizadas usam imagens sintéticas e uma mesa renderizada pelo aplicativo. Não houve acesso à memória, alteração de arquivos de terceiros, automação de tacadas ou evasão de restrições do Android. Conteúdo protegido pode ficar preto conforme o sistema.

## Atualização e segurança de apresentação

O pipeline existente tem um consumidor e um único frame pendente substituível; o ImageReader usa acquireLatestImage. Até três buffers RGBA são reutilizados, com captura limitada a 1280 pixels na maior dimensão e teto de 15 FPS. A detecção usa uma grade de até 400 pixels de largura e preserva apenas uma miniatura para calibração, sem gravar frames em disco nem enviá-los pela rede.

Alterações de controles, calibração, pausa ou sessão invalidam resultados em processamento por revisão. Resultados antigos não são reapresentados: a sobreposição oculta previsões após 700 ms sem quadro recente, na resolução/orientação errada, em cenas ambíguas ou sem direção confiável. A frequência efetiva depende do dispositivo. Diagnósticos de captura mostram FPS, latência após recebimento, descartes, heap, PSS e memória de buffers.

## Módulos existentes

| Módulo | Responsabilidade |
| --- | --- |
| app | Navegação, telas Compose, consentimento, calibração por imagem e mesa de teste |
| physics | Motor geométrico Kotlin independente de Android; mantido sem reescrita |
| vision-api | Frames RGBA, pipeline limitado, observações/configuração e snapshots de análise |
| recognition | Detecção conservadora, assinatura de cor, direção e adaptação ao motor |
| calibration | Preferências do simulador e calibração visual persistente |
| capture | Foreground service mediaProjection, ImageReader e métricas |
| overlay | Serviço specialUse, botão/menu, Canvas transparente e desenho alinhado |

O motor calcula primeiro contato, ponto de contato, direção inicial do alvo e reflexões considerando raios. Usa estimativas geométricas ideais: não conhece parâmetros internos do 8 Ball Pool e não calcula força, atrito, efeito, distância de parada, caçapas, trajetória residual da branca ou uma dinâmica completa de impactos em cadeia.

## Compilar sem Android Studio

JDK 17 e Android SDK Command-line Tools; Gradle Wrapper 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01.

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
sdkmanager --licenses
export ANDROID_HOME=/caminho/para/android-sdk
./gradlew --no-daemon :physics:test :vision-api:test :recognition:test :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.poolaim.app/.MainActivity
./gradlew connectedDebugAndroidTest
```

No aplicativo, **Mesa para testar análise → Iniciar teste visual** captura uma mesa Canvas com três bolas e uma mira que muda pelo slider. É o caminho de demonstração do fluxo real de visão, diferente das linhas geométricas fixas em **Testar sobreposição**.

O workflow `.github/workflows/android.yml` executa testes JVM, assembleDebug e lint, publica o APK e executa instrumentação em API 35 com KVM. Ele depende do envio dos arquivos ao GitHub; não foi executado remotamente nesta tarefa.

Os resultados efetivamente obtidos, falhas intermediárias e limites de validação constam em [docs/VALIDATION.md](docs/VALIDATION.md).

## Pacote de teste estabilizado

O APK de entrega está em `dist/Pool-Aim-Assist-debug.apk`, acompanhado por checksum e guia. Veja [instalação no Galaxy Tab S9 Plus](docs/INSTALLATION.md) e [validação desta revisão](docs/VALIDATION.md). Ao encerrar a captura, as miniaturas globais são descartadas; o processador não retém pixels da imagem anterior. Backup/transferência de dados e tráfego HTTP em texto claro estão desabilitados.

No GitHub: **Actions → Android Debug → execução → Summary → Artifacts → Pool-Aim-Assist-debug**. O ZIP publicado pelo job build contém o APK, SHA256SUMS e LEIA-ME.md e fica disponível por 14 dias. O job de teste de dispositivo é independente. É necessária uma execução real do workflow após o envio do código; configurar o arquivo localmente não publica o artefato.
