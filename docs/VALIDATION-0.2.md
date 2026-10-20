# Revisão 0.2.0 — painel flutuante

A versão reaproveita os módulos existentes. O launcher agora é uma Activity pequena para permissões; o serviço de sobreposição continua independente. O ícone abre o painel, e Iniciar pede MediaProjection e retorna ao aplicativo anterior. A tela completa fica acessível por Mais opções → Configurações/calibração.

O painel principal tem Iniciar, Parar, transparência e quantidade de tabelas (0–2). O ajuste de reflexões agora alimenta VisualProcessor/GeometryEngine e é persistido com a opacidade e visibilidade. Antes, a previsão visual usava sempre duas reflexões, e o controle antigo alterava só o simulador.

O menu suspende a interpretação para evitar realimentação visual; fechar/recolher permite novos frames. Após salvar uma calibração, o editor fecha e a análise é retomada quando há sessão autorizada. As regras de confiança e expiração não foram enfraquecidas.

## Validação local

`./gradlew --no-daemon :physics:test :vision-api:test :recognition:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`

BUILD SUCCESSFUL. **58 testes JVM**, zero falhas/erros/ignorados. Inclui teste que altera o limite 2 → 0 → 1, invalida resultados anteriores e verifica as reflexões realmente produzidas pela previsão visual. Lint sem erros; avisos relativos a orientação/strings de interface.

Foi acrescentado FloatingLaunchTest, que usa o launcher real, ajusta os controles, confirma o diálogo MediaProjection, verifica frames sem laboratório visível e encerra a captura pelo painel. OverlayTest foi adaptado para abrir Mais opções; os demais testes de captura, visão, desenho e encerramento foram mantidos. O resultado instrumentado remoto será registrado após a execução.

## Limitação do diagnóstico

A ferramenta recusou o vídeo enviado por exceder 32 MiB; não foi possível assistir nem extrair frames. As correções desta versão resolvem problemas verificáveis do código e o fluxo solicitado, sem afirmar que o reconhecimento sobre a gravação do usuário foi validado. Nenhum frame real do 8 Ball Pool ou hardware Samsung físico foi testado nesta revisão.
