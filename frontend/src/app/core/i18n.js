/* global angular */
(function () {
  'use strict';
  var messages = {
    'pt-BR': {
      skip:'Ir para o conteúdo',language:'Idioma',theme:'Alternar tema',footer:'Clareza para suas finanças.',
      welcome:'Suas finanças começam com um espaço organizado.',intro:'Separe a vida pessoal e o negócio. Escolha um workspace para manter cada contexto no seu lugar.',
      login:'Entrar',register:'Criar conta',email:'E-mail',password:'Senha',name:'Nome',forgot:'Esqueci minha senha',
      loginTitle:'Bom ter você por aqui.',registerTitle:'Comece pelo essencial.',forgotTitle:'Recupere seu acesso.',verifyTitle:'Confirme seu e-mail.',resetTitle:'Escolha uma nova senha.',
      passwordHint:'Use pelo menos 12 caracteres e no máximo 72 bytes UTF-8.',send:'Enviar',verify:'Confirmar e-mail',reset:'Alterar senha',back:'Voltar ao login',
      checkEmail:'Confira seu e-mail para continuar. Se o cadastro for elegível, uma mensagem será enviada.',verified:'E-mail confirmado. Você já pode entrar.',passwordReset:'Senha alterada. Entre novamente.',
      error:'Não foi possível concluir. Confira os dados e tente novamente.',unauthorized:'Acesso inválido ou expirado. Confira suas credenciais e a confirmação de e-mail.',rateLimited:'Muitas tentativas. Aguarde um minuto.',
      workspaces:'Seus workspaces',workspaceIntro:'Um espaço para cada parte da sua vida financeira.',createWorkspace:'Criar workspace',workspaceName:'Nome do workspace',personal:'Pessoal',business:'Negócio',
      kind:'Tipo',currency:'Moeda',timezone:'Fuso horário',create:'Criar',logout:'Sair',empty:'Seu primeiro workspace começa aqui.',emptyHint:'Crie um espaço pessoal ou para seu negócio.',
      loading:'Carregando…',previous:'Anterior',next:'Próxima',select:'Selecionar',selected:'Selecionado',workspaceSelected:'Workspace selecionado',foundation:'As ferramentas financeiras serão disponibilizadas nas próximas etapas.',
      pending:'Processando…',resend:'Reenviar confirmação',newPassword:'Nova senha'
    },
    en: {
      skip:'Skip to content',language:'Language',theme:'Toggle theme',footer:'Clarity for your finances.',
      welcome:'Your finances start with an organized space.',intro:'Separate personal life and business. Choose a workspace to keep each financial context together.',
      login:'Sign in',register:'Create account',email:'Email',password:'Password',name:'Name',forgot:'Forgot password',
      loginTitle:'Welcome back.',registerTitle:'Start with the essentials.',forgotTitle:'Recover your access.',verifyTitle:'Confirm your email.',resetTitle:'Choose a new password.',
      passwordHint:'Use at least 12 characters and at most 72 UTF-8 bytes.',send:'Send',verify:'Confirm email',reset:'Change password',back:'Back to sign in',
      checkEmail:'Check your email to continue. If the account is eligible, a message will be sent.',verified:'Email confirmed. You can sign in now.',passwordReset:'Password changed. Sign in again.',
      error:'Unable to complete the request. Check your details and try again.',unauthorized:'Access is invalid or expired. Check your credentials and email verification.',rateLimited:'Too many attempts. Wait one minute.',
      workspaces:'Your workspaces',workspaceIntro:'A space for each part of your financial life.',createWorkspace:'Create workspace',workspaceName:'Workspace name',personal:'Personal',business:'Business',
      kind:'Type',currency:'Currency',timezone:'Timezone',create:'Create',logout:'Sign out',empty:'Your first workspace starts here.',emptyHint:'Create a personal or business space.',
      loading:'Loading…',previous:'Previous',next:'Next',select:'Select',selected:'Selected',workspaceSelected:'Workspace selected',foundation:'Financial tools will become available in the next implementation stages.',pending:'Processing…',resend:'Resend confirmation',newPassword:'New password'
    },
    es: {
      skip:'Ir al contenido',language:'Idioma',theme:'Cambiar tema',footer:'Claridad para tus finanzas.',
      welcome:'Tus finanzas empiezan con un espacio organizado.',intro:'Separa tu vida personal y tu negocio. Elige un workspace para mantener cada contexto en su lugar.',
      login:'Entrar',register:'Crear cuenta',email:'Correo electrónico',password:'Contraseña',name:'Nombre',forgot:'Olvidé mi contraseña',
      loginTitle:'Te damos la bienvenida.',registerTitle:'Empieza por lo esencial.',forgotTitle:'Recupera tu acceso.',verifyTitle:'Confirma tu correo.',resetTitle:'Elige una nueva contraseña.',
      passwordHint:'Usa al menos 12 caracteres y como máximo 72 bytes UTF-8.',send:'Enviar',verify:'Confirmar correo',reset:'Cambiar contraseña',back:'Volver al inicio',
      checkEmail:'Revisa tu correo para continuar. Si la cuenta cumple los requisitos, se enviará un mensaje.',verified:'Correo confirmado. Ya puedes entrar.',passwordReset:'Contraseña cambiada. Vuelve a entrar.',
      error:'No se pudo completar. Revisa los datos e inténtalo de nuevo.',unauthorized:'Acceso inválido o caducado. Revisa tus credenciales y la verificación del correo.',rateLimited:'Demasiados intentos. Espera un minuto.',
      workspaces:'Tus workspaces',workspaceIntro:'Un espacio para cada parte de tu vida financiera.',createWorkspace:'Crear workspace',workspaceName:'Nombre del workspace',personal:'Personal',business:'Negocio',
      kind:'Tipo',currency:'Moneda',timezone:'Zona horaria',create:'Crear',logout:'Salir',empty:'Tu primer workspace empieza aquí.',emptyHint:'Crea un espacio personal o para tu negocio.',
      loading:'Cargando…',previous:'Anterior',next:'Siguiente',select:'Seleccionar',selected:'Seleccionado',workspaceSelected:'Workspace seleccionado',foundation:'Las herramientas financieras estarán disponibles en las próximas etapas.',pending:'Procesando…',resend:'Reenviar confirmación',newPassword:'Nueva contraseña'
    }
  };
  angular.module('financehub').factory('I18n', function () { return { language: 'pt-BR', messages: messages }; })
    .filter('t', ['I18n', function (i18n) {
      var translate = function (key) { return (messages[i18n.language] || messages.en)[key] || key; };
      translate.$stateful = true; return translate;
    }]);
}());
