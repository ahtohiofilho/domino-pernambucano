package com.ahtohiofilho.dominopernambucano.online

/*
 * Marca repositorios de desenvolvimento sem ticker autoritativo proprio.
 *
 * O cliente pode enviar REQUEST_SNAPSHOT como gatilho para:
 * - decisao de um participante APPLICATION;
 * - resolucao imediata de um passe obrigatorio;
 * - resolucao de timeout quando o relogio projetado chega a zero.
 *
 * O backend remoto nao implementa esta capacidade.
 */
internal interface OnlineClientDrivenFakeProgression
