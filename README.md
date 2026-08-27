# Sense Ultra Shizuku Edition

Projeto independente inspirado nas funcionalidades observáveis do Sense Ultra Pro, sem copiar código, assinatura, licença ou recursos proprietários.

## Objetivo
Executar, quando o Android/Shizuku permitir, funções de diagnóstico e configuração úteis para jogos e mobilador sem root.

## Módulos atuais
- Status e autorização Shizuku
- Informações do aparelho/display
- Resolução e densidade (wm size / wm density)
- Restauração de resolução/densidade
- Diagnóstico de input/touchscreen
- Geometria/alinhamento do touch
- EVDEV/getevent (leitura/diagnóstico)
- Perfis locais de sensibilidade
- Detecção dos pacotes Free Fire/Free Fire MAX
- Diagnóstico de FPS/refresh/display
- Limpeza de cache via cmd package
- Console Shizuku

## Importante sobre Shizuku
Quando iniciado pelo ADB, o UserService roda com identidade shell (UID 2000), e não root. Por isso algumas operações de baixo nível, especialmente captura/reinjeção de eventos e alterações protegidas pelo fabricante, podem ser recusadas pelo Android/HyperOS. O app deve reportar o erro em vez de fingir que aplicou.

## Build
Abra o projeto no Android Studio, aguarde o Gradle sincronizar e gere um APK de debug em Build > Build APK(s).

Dependência Shizuku API: 13.1.5.
