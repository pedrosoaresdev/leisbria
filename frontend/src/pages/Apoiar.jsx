import { useEffect, useRef } from 'react';
import QRCode from 'qrcode';
import Icon from '../components/Icon';
import { Notice, copiar, useToast } from '../components/ui';
import { BITCOIN_ADDRESS } from '../config';

/** Página de apoio ao projeto por doação em bitcoin (endereço on-chain, rede Bitcoin). */
export default function Apoiar() {
  const toast = useToast();
  const canvas = useRef(null);
  const uri = `bitcoin:${BITCOIN_ADDRESS}`;

  useEffect(() => {
    if (canvas.current) QRCode.toCanvas(canvas.current, uri, { width: 220, margin: 1, color: { dark: '#0b1f3a' } });
  }, [uri]);

  return (
    <div className="container page" style={{ maxWidth: 760 }}>
      <div className="page-title"><h1>Apoie a Pandecta</h1><p>A plataforma é mantida de forma independente. Doações ajudam a custear servidores, indexação e integração com novas fontes oficiais.</p></div>
      <section className="panel">
        <div className="panel-head"><Icon name="sparkle" style={{ color: 'var(--gold-600)' }} /><h2>Doação em Bitcoin (BTC)</h2></div>
        <div className="panel-body stack" style={{ alignItems: 'center', textAlign: 'center' }}>
          <canvas ref={canvas} aria-label="QR Code do endereço bitcoin para doação" style={{ border: '8px solid #fff', borderRadius: 8, boxShadow: 'var(--shadow)' }} />
          <div className="small muted">Leia o QR Code com sua carteira ou copie o endereço abaixo.</div>
          <code style={{ fontFamily: 'var(--font-mono)', fontSize: '.95rem', background: 'var(--gray-100)', border: '1px solid var(--gray-200)', borderRadius: 8, padding: '10px 12px', wordBreak: 'break-all', maxWidth: '100%' }}>
            {BITCOIN_ADDRESS}
          </code>
          <div className="row" style={{ justifyContent: 'center' }}>
            <button className="btn btn-gold" onClick={() => copiar(BITCOIN_ADDRESS, toast, 'Endereço copiado')}><Icon name="copy" size={16} /> Copiar endereço</button>
            <a className="btn btn-ghost" href={uri}><Icon name="external" size={16} /> Abrir na carteira</a>
          </div>
        </div>
        <div className="panel-body" style={{ paddingTop: 0 }}>
          <Notice kind="warn">
            Envie somente <strong>bitcoin (BTC) pela rede Bitcoin</strong>. Envios de outras moedas ou redes podem ser perdidos.
            Confira os primeiros e os últimos caracteres do endereço na sua carteira antes de confirmar. Transações em blockchain são irreversíveis.
          </Notice>
        </div>
      </section>
      <p className="xsmall muted" style={{ marginTop: 12 }}>Doações são voluntárias e não dão acesso a recursos adicionais nem a orientação jurídica.</p>
    </div>
  );
}
