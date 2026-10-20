# Pool Aim Assist 0.2.0 — instalar e usar

## Baixar e atualizar

No GitHub, abra **Actions → Android Debug → execução aprovada mais recente → Summary → Artifacts → Pool-Aim-Assist-debug**. O ZIP contém `Pool-Aim-Assist-debug.apk`, `SHA256SUMS` e este guia.

No tablet, abra o ZIP em **Meus Arquivos**, toque em **Extrair** e abra o APK extraído. Permita instalar desta fonte se o Android solicitar. Toque em **Atualizar/Instalar → Abrir**. A versão instalada deve ser **0.2.0 (código 2)**. Se houver assinatura diferente, o Android pode exigir desinstalar a versão anterior, o que apaga as preferências; APKs de execuções distintas podem usar chaves debug diferentes.

## Usar o painel compacto

1. O ícone abre os controles flutuantes, sem o laboratório em tela cheia. Na primeira abertura, conceda **Aparecer sobre outros aplicativos** e volte. O Android também pode solicitar notificações.
2. Abra o aplicativo que deseja analisar e toque em **PA → Iniciar**. Confirme o consentimento de captura do Android. A janela temporária fecha, retornando ao aplicativo anterior. Não há captura sem esse consentimento.
3. Arraste **PA para fora do pano da mesa**. O botão sobre a mesa pode impedir a detecção. O menu também precisa estar fechado para não analisar seus próprios controles.
4. Toque em PA para ajustar **Transparência das linhas (0–90%)** ou **Quantidade de tabelas (0–2)**. Os ajustes são salvos e aplicados ao motor visual. **Recolher painel** retoma a interpretação dos frames.
5. **Parar** encerra a captura e remove as linhas, mantendo PA disponível. Outra captura exige nova autorização. **Mais opções → Encerrar aplicativo** remove também a sobreposição.

Uma pequena janela temporária pode aparecer para abrir as permissões. Os diálogos do Android são obrigatórios. O painel não mantém o laboratório em primeiro plano durante a análise.

## Se as linhas não aparecerem

Abra PA e leia o status. A detecção automática é experimental: pano, bolas e mira precisam ser reconhecidos com confiança em dois frames recentes. Linhas antigas/ambíguas são ocultadas. Elas não são desenhadas sobre telas sem mesa ou sem direção confiável.

Use **PA → Mais opções → Calibrar mesa** para editar um quadro congelado: selecione limites, marque primeiro a branca e depois as demais bolas e salve. Ao salvar, o editor fecha e a análise retoma se houver sessão autorizada. Recalibre após as bolas se moverem ou após mudanças de orientação/visual. A direção pode ser ajustada em **Mais opções → Usar direção manual**. Para ajustes avançados, **Mais opções → Configurações** abre o laboratório; **Voltar ao botão flutuante** fecha essa tela.

As previsões são geométricas, sem força, atrito ou efeito. Não há automação de tacadas. O aplicativo respeita bloqueios de captura/sobreposição do Android e de outros aplicativos.

## Validação e privacidade

A versão anterior passou em emuladores Android 9/API 28 e Android 15/API 35. O resultado dos testes da abertura compacta está em `docs/VALIDATION-0.2.md`. **O funcionamento sobre o 8 Ball Pool real e no Galaxy Tab S9 Plus físico precisa de validação.**

O vídeo de uso enviado excedeu o limite de transferência de 32 MiB e não pôde ser examinado nesta revisão. Não se afirma que a causa específica mostrada nele foi corrigida. Um trecho menor permite investigar a falha de reconhecimento.

Captura exige autorização por sessão. Os frames ficam em memória, sem envio ou gravação pelo aplicativo. Backup/transferência estão desabilitados; o APK não pede permissão de internet, câmera, microfone, localização ou armazenamento.
