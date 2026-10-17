import type { ReactNode } from 'react';

// Renders a deliberately small Markdown subset (headings, bold, italic,
// inline code, code blocks, lists, blockquotes, links) to React elements.
//
// Safety comes from construction rather than sanitization: source text is
// only ever placed in React text nodes, which React escapes, and link hrefs
// are limited to http(s). There is no dangerouslySetInnerHTML in this file,
// so prep notes written by one user cannot inject markup into their own
// (or anyone else's) session.

const INLINE_PATTERN =
  /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(\[[^\]]+\]\((?:https?:\/\/[^)\s]+)\))/g;

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let lastIndex = 0;
  let part = 0;
  for (const match of text.matchAll(INLINE_PATTERN)) {
    const index = match.index ?? 0;
    if (index > lastIndex) {
      nodes.push(text.slice(lastIndex, index));
    }
    const token = match[0];
    const key = `${keyPrefix}-${part++}`;
    if (token.startsWith('`')) {
      nodes.push(<code key={key}>{token.slice(1, -1)}</code>);
    } else if (token.startsWith('**')) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('*')) {
      nodes.push(<em key={key}>{token.slice(1, -1)}</em>);
    } else {
      const link = /^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/.exec(token);
      if (link) {
        nodes.push(
          <a key={key} href={link[2]} target="_blank" rel="noreferrer">
            {link[1]}
          </a>,
        );
      } else {
        nodes.push(token);
      }
    }
    lastIndex = index + token.length;
  }
  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes;
}

export default function Markdown({ source }: { source: string }) {
  const blocks: ReactNode[] = [];
  const lines = source.split('\n');
  let paragraph: string[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;
  let quote: string[] = [];
  let code: string[] | null = null;

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push(<p key={blocks.length}>{renderInline(paragraph.join(' '), `p${blocks.length}`)}</p>);
      paragraph = [];
    }
  };
  const flushList = () => {
    if (list) {
      const items = list.items.map((item, i) => (
        <li key={i}>{renderInline(item, `li${blocks.length}-${i}`)}</li>
      ));
      blocks.push(
        list.ordered ? (
          <ol key={blocks.length}>{items}</ol>
        ) : (
          <ul key={blocks.length}>{items}</ul>
        ),
      );
      list = null;
    }
  };
  const flushQuote = () => {
    if (quote.length > 0) {
      blocks.push(
        <blockquote key={blocks.length}>
          {renderInline(quote.join(' '), `q${blocks.length}`)}
        </blockquote>,
      );
      quote = [];
    }
  };
  const flushAll = () => {
    flushParagraph();
    flushList();
    flushQuote();
  };

  for (const line of lines) {
    if (code !== null) {
      if (line.trim().startsWith('```')) {
        blocks.push(
          <pre key={blocks.length}>
            <code>{code.join('\n')}</code>
          </pre>,
        );
        code = null;
      } else {
        code.push(line);
      }
      continue;
    }
    if (line.trim().startsWith('```')) {
      flushAll();
      code = [];
      continue;
    }

    const heading = /^(#{1,4})\s+(.*)$/.exec(line);
    if (heading) {
      flushAll();
      const text = renderInline(heading[2], `h${blocks.length}`);
      const level = heading[1].length;
      blocks.push(
        level === 1 ? (
          <h3 key={blocks.length}>{text}</h3>
        ) : level === 2 ? (
          <h4 key={blocks.length}>{text}</h4>
        ) : (
          <h5 key={blocks.length}>{text}</h5>
        ),
      );
      continue;
    }

    const bullet = /^\s*[-*]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      flushParagraph();
      flushQuote();
      const ordered = numbered !== null;
      if (!list || list.ordered !== ordered) {
        flushList();
        list = { ordered, items: [] };
      }
      list.items.push((bullet ?? numbered)![1]);
      continue;
    }

    if (line.startsWith('> ')) {
      flushParagraph();
      flushList();
      quote.push(line.slice(2));
      continue;
    }

    if (line.trim() === '') {
      flushAll();
      continue;
    }

    flushList();
    flushQuote();
    paragraph.push(line.trim());
  }
  if (code !== null) {
    // An unterminated fence still renders as a code block, not as prose.
    blocks.push(
      <pre key={blocks.length}>
        <code>{code.join('\n')}</code>
      </pre>,
    );
  }
  flushAll();

  return <div className="markdown">{blocks}</div>;
}
