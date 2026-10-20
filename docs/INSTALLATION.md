# Pool Aim Assist — APK de teste

Arquivo: `Pool-Aim-Assist-debug.apk`. Pacote: `com.poolaim.app`. Versão debug para teste local, não publicada na Play Store. Compatível a partir de Android 8/API 26; alvo API 35. Interface em paisagem, preparada para teste no Samsung Galaxy Tab S9 Plus.

## Instalar no tablet

1. Transfira o APK para o tablet e abra pelo aplicativo Meus Arquivos.
2. Se o Android solicitar, permita **Instalar apps desconhecidos** apenas para o aplicativo usado para abrir o APK. As opções podem variar com a versão do One UI e com políticas do dispositivo.
3. Instale e abra **Pool Aim Assist**. Uma instalação anterior com o mesmo pacote e outra chave de assinatura pode exigir remoção prévia; desinstalar apaga preferências/calibração.

Com adb e depuração USB autorizada:

```sh
adb install -r Pool-Aim-Assist-debug.apk
adb shell am start -n com.poolaim.app/.MainActivity
```

## Primeiro teste

1. Abra **Iniciar assistência visual → Mesa para testar análise → Iniciar teste visual**.
2. Conceda a permissão de sobreposição e confirme o consentimento de captura no diálogo do Android. No Android 14+ a captura solicita a tela inteira para alinhar as linhas.
3. Mantenha o botão **PA fora do pano**. Altere a direção no slider da mesa e observe as previsões. Magenta representa a branca, laranja a bola-alvo e azul as reflexões. O trecho da mira original é preservado.
4. Teste **PA → Pausar/Retomar**, mostrar/ocultar linhas e direção manual.
5. Use **Parar** ou a ação da notificação para encerrar a captura. **Encerrar aplicativo** no menu também remove os serviços e a tarefa do Pool Aim Assist.

Para outro aplicativo: inicie a captura, abra a mesa e mantenha PA fora da área jogável. Se a detecção falhar, use **PA → Calibrar mesa** para congelar o quadro. Ajuste os limites; marque primeiro a branca e depois todas as bolas. Confirme e salve. A direção manual está disponível na tela e no menu flutuante. Recalibre após movimentos das bolas e mudanças visuais.

## Limites e privacidade

A detecção automática é experimental. Os testes disponíveis usam imagens sintéticas e a mesa Canvas do próprio aplicativo. **O funcionamento sobre o 8 Ball Pool real e no Galaxy Tab S9 Plus físico ainda precisa de validação.** Os testes instrumentados passaram em emuladores Android 9/API 28 e Android 15/API 35; isso não substitui testes no hardware Samsung/One UI. Não há garantia de acerto ou desempenho de 15 FPS; esse valor é um teto de captura. Estimativas não incluem força, efeito ou atrito.

As linhas desaparecem se os dados ficam antigos ou ambíguos. Android e outros aplicativos podem ocultar sobreposição ou proteger conteúdo; o aplicativo não contorna essas regras. Não há automação de tacadas.

Captura exige autorização por sessão. Os frames ficam em memória e não são enviados nem gravados em disco pelo aplicativo. Parar descarta os quadros globais e a miniatura de calibração; uma tela de edição já aberta pode manter sua cópia até sair dela. Preferências e geometria/cor da calibração ficam no armazenamento privado, com backup/transferência desabilitados. A captura usa serviço/notificação próprios e pode ser encerrada pelo Android.

## Onde baixar no GitHub

Execução verificada com build e testes Android 15 aprovados: [Android Debug #6](https://github.com/OtavioRezende-site/pool-aim-assist/actions/runs/39448424388). Para baixar:

**OtavioRezende-site/pool-aim-assist → Actions → Android Debug → execução desejada → Summary → Artifacts → Pool-Aim-Assist-debug**.

O ZIP contém `Pool-Aim-Assist-debug.apk`, `SHA256SUMS` e `LEIA-ME.md`. Extraia o ZIP antes de instalar o APK. O artefato fica disponível por 14 dias e depende de uma execução real; configurar o YAML localmente não o publica. O job de dispositivo é separado, permitindo baixar o APK se o build passar e o emulador do CI tiver problemas.
