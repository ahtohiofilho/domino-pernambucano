# Arquitetura vinculante de identidade multiprovedor

## Estado

A conta do Dominó PE é a identidade canônica do produto. Identidades externas
são somente meios independentes de localizar, vincular ou recuperar essa
conta.

Provedores reservados no schema 11:

- `GOOGLE`: identidade Google verificada pelo backend;
- `EMAIL`: identidade de e-mail verificada por fluxo próprio do Dominó PE.

## Regras invariantes

1. Nenhum provedor externo é a própria conta Dominó PE.
2. Uma conta pode possuir identidades de provedores diferentes.
3. Uma conta pode possuir no máximo uma identidade por provedor.
4. Uma identidade externa pode apontar para apenas uma conta canônica.
5. O sistema nunca funde duas contas silenciosamente.
6. Subjects iguais em provedores diferentes pertencem a namespaces distintos.
7. Perfil, histórico, partidas, ranking e configurações pertencem à conta
   Dominó PE, não ao provedor.
8. A perda ou troca de um provedor não pode apagar o progresso da conta.
9. O vínculo adicional exige uma sessão de conta válida.
10. A recuperação por identidade externa somente emite sessão para a conta
    previamente vinculada.

## Login Google

O login Google existente permanece como método externo principal no MVP. O
backend valida a identidade Google e vincula ou recupera a conta canônica
segundo as regras invariantes.

## Login por e-mail

Um fluxo futuro de e-mail poderá ser implementado sem senha tradicional,
preferencialmente por código de uso único, com expiração, limitação de
tentativas e prevenção de enumeração.

## Regra de adoção de novos provedores

Nenhum novo provedor deve ser integrado sem:

- benefício claro para o usuário;
- baixo atrito operacional;
- configuração reproduzível;
- segurança verificável no backend;
- custo de manutenção proporcional ao retorno esperado;
- homologação real concluída antes de exposição no produto.

A infraestrutura genérica de identidades vinculadas não obriga a exposição de
todos os provedores tecnicamente possíveis.
