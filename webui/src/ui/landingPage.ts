// The landing page (#/ before a session, #/about at any time): what the telerobot
// was, its history and the builds people made with it, and the two ways in, the
// 2000 Java applet page and the 1996 forms page. Laid out as a tidied 1990s page,
// like the forms page.

import galleryJson from '../content/gallery.json';
import oldSiteJson from '../content/old-site.json';
import { h } from './dom';
import { DALTON_THESIS, WIKIPEDIA } from './wakingPage';

export interface GalleryImage { src: string; alt: string }
export interface HallEntry { title: string; by: string | null; date: string | null; note: string | null; moves: number | null; images: GalleryImage[] }
export interface TowerEntry { heightMm: number; by: string | null; date: string | null; images: GalleryImage[] }
export interface CreationEntry { title: string | null; by: string | null; date: string | null; images: GalleryImage[] }
export interface Gallery { hall: HallEntry[]; towers: TowerEntry[]; creations: CreationEntry[] }

/** Builds people made, saved from the original site (website/htdocs and MOM/htdocs in the archive). */
export const GALLERY: Gallery = galleryJson as Gallery;

export interface OldPage { group: string; title: string; href: string; date: string; what: string }
/** Pages preserved from the original site (public/old, scripts/preserve-old-site.mjs), in groups. */
export const OLD_SITE: OldPage[] = oldSiteJson as OldPage[];

/** Under the ways in: the old look is on purpose. */
export const STYLING_NOTE = '2000s styling preserved: the applet page looks as it did in 2000, and the forms page as it did in 1996.';

/** Where each way in goes: the login page, then that interface once the session starts. */
export const APPLET_HASH = '#/login';
export const FORMS_HASH = '#/classic';

/** Year milestones, from the old site's own pages, Wikipedia and the thesis. */
export const HISTORY: readonly { year: string; text: string }[] = [
  { year: '1994', text: 'Ken Taylor puts the telerobot on the web, when the only live device there was a camera watching a coffee pot at Cambridge. It was one of the first robots anyone could control.' },
  { year: '1996', text: 'Ken Taylor and James Trevelyan win a JARA Award at the International Symposium on Industrial Robots in Milan for “Australia’s Telerobot On The Web”. The original arm is replaced by an ABB IRB1400.' },
  { year: '1997', text: 'A US$100 prize for the best improvement to the operator interface. Visitors design their own control pages.' },
  { year: '1998', text: 'Building works: the robot spends months at Questacon, the national science museum.' },
  { year: '1999', text: 'About 200 people a day make over 1000 moves. The tallest tower reaches 785 mm.' },
  { year: '2000', text: 'Barney Dalton’s Java applet adds live views with the robot drawn over them, a command editor and chat. Over 10,000 logins that year.' },
  { year: '2001', text: 'Barney Dalton’s PhD thesis, “Techniques for Web Telerobotics”, describes the system.' },
  { year: '2026', text: 'This re-creation: the original 2000 server software runs again, driving a simulated robot, cameras and blocks.' },
];

/** The old site's "In The News" page, abridged. */
export const PRESS: readonly string[] = [
  '“Robots Computer Link A Knockout”, The West Australian, 5 November 1994',
  'US ABC Network evening news, 2 January 1996',
  'SBS TV evening news, Australia, 21 January 1996',
  '“Recovery”, ABC TV Australia, 15 November 1997',
  '“Safari”, NRK (Norwegian Broadcasting Corporation), 11 November 1997',
  '“Control a robot Down Under”, Design News, 20 April 1998',
];

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/** "2000-02-07" -> "7 February 2000"; anything else as it is. */
export function longDate(iso: string | null): string {
  const m = iso ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso) : null;
  if (!m) return iso ?? '';
  return `${Number(m[3])} ${MONTHS[Number(m[2]) - 1] ?? ''} ${m[1]}`;
}

function byline(by: string | null, date: string | null): string {
  const parts = [by ? `by ${by}` : '', longDate(date)].filter(Boolean);
  return parts.join(', ');
}

function photos(images: GalleryImage[]): HTMLElement {
  return h('div', { class: 'landing-photos' },
    ...images.map((i) => h('img', { src: i.src, alt: i.alt, loading: 'lazy', decoding: 'async', class: 'landing-photo' })));
}

function link(text: string, href: string, newTab = false): HTMLAnchorElement {
  return newTab ? h('a', { href, text, target: '_blank', rel: 'noopener' }) : h('a', { href, text });
}

export class LandingPage {
  readonly el: HTMLElement;

  constructor(gallery: Gallery = GALLERY) {
    const hero = gallery.hall[0]?.images[0];
    const way = (hash: string, title: string, what: string) =>
      h('li', { class: 'landing-way' }, h('a', { href: hash, class: 'landing-go', text: title }), h('div', { text: what }));

    this.el = h(
      'div',
      { class: 'page classic-page landing-page' },
      h('h1', { text: 'The UWA Telerobot' }),
      h('p', { class: 'landing-lede' },
        'From 1994, anyone with a web browser could drive a real industrial robot arm at the University of ' +
          'Western Australia in Perth. Visitors moved wooden blocks around a table, watching through the cameras, ' +
          'and built towers, bridges and castles.'),

      h('h2', { text: 'Drive the robot' }),
      h('p', {}, 'The robot sleeps when no one is using it; waking it takes under a minute. Log in as a guest with a ' +
        'nickname, or sign in. Behind the pages, the original 2000 server software drives a simulated robot.'),
      h('ul', { class: 'landing-ways' },
        way(APPLET_HASH, 'The Java applet interface (2000)',
          'Live camera views with the robot drawn over them, a command editor, paths, and chat with other operators.'),
        way(FORMS_HASH, 'The forms interface (1996)',
          'One move per page, as it was first driven: type a position, or click the same point in two photos.')),
      h('p', { class: 'landing-styling', text: STYLING_NOTE }),
      h('p', {}, link('Brief help', '/help.html', true), ' · ', link('Moves people have made here', '#/history')),
      hero ? h('figure', { class: 'landing-hero' }, h('img', { src: hero.src, alt: hero.alt, class: 'landing-photo' }),
        h('figcaption', { text: `${gallery.hall[0]!.title}${gallery.hall[0]!.by ? `, by ${gallery.hall[0]!.by}` : ''}` })) : h('span'),

      h('h2', { text: 'About the telerobot' }),
      h('p', {}, 'The telerobot was part of research at UWA into controlling physical devices over the Internet. ' +
        'Ken Taylor began it; Barney Dalton extended it for his PhD; many other people contributed.'),
      h('p', {}, 'An industrial arm stood over a table of wooden blocks, with cameras around it. Operators asked the ' +
        'robot to move by typing coordinates or clicking on the pictures, then saw new pictures of the table. ' +
        'It ran 24 hours a day: some visitors made a single move, others built for hours.'),

      h('h2', { text: 'History' }),
      h('dl', { class: 'landing-history' },
        ...HISTORY.flatMap((e) => [h('dt', { text: e.year }), h('dd', { text: e.text })])),

      ...this.hallOfFame(gallery.hall),
      ...this.towers(gallery.towers),
      ...this.creations(gallery.creations),

      ...this.oldSite(OLD_SITE),

      h('h2', { text: 'In the news' }),
      h('ul', {}, ...PRESS.map((p) => h('li', { text: p }))),
      h('p', {}, link('The original “In The News” page', '/old/news.html'), ', with radio, television and magazines from 1994 to 1998.'),

      h('h2', { text: 'Read more' }),
      h('ul', {},
        h('li', {}, link('The UWA Telerobot on Wikipedia', WIKIPEDIA, true)),
        h('li', {}, link('Techniques for Web Telerobotics', DALTON_THESIS, true), ', Barney Dalton’s PhD thesis (2001, PDF)')),
      h('hr'),
      h('p', { class: 'classic-footer' },
        'A re-creation of Australia’s Telerobot on the Web (telerobot.mech.uwa.edu.au). ' +
          'The photos and their captions are from the original site.'),
    );
  }

  private oldSite(pages: OldPage[]): HTMLElement[] {
    if (!pages.length) return [];
    const groups = [...new Set(pages.map((p) => p.group))];
    return [
      h('h2', { text: 'From the original site' }),
      h('p', { text: 'Pages kept as they were, with the links that no longer lead anywhere removed.' }),
      ...groups.flatMap((g) => [
        h('h3', { text: g }),
        h('ul', { class: 'landing-old' }, ...pages.filter((p) => p.group === g).map((p) =>
          h('li', {}, link(p.title, p.href), ` (${p.date}): ${p.what}`))),
      ]),
    ];
  }

  private hallOfFame(hall: HallEntry[]): HTMLElement[] {
    if (!hall.length) return [];
    return [
      h('h2', { text: 'Hall of Fame' }),
      h('p', { text: 'From the original site’s Hall of Fame, 1996 to 1999, with its captions and some builders’ own words.' }),
      ...hall.map((e) => h('section', { class: 'landing-build' },
        h('h3', { text: e.title }),
        photos(e.images),
        h('p', { class: 'landing-byline', text: [byline(e.by, e.date), e.moves ? `${e.moves} moves` : ''].filter(Boolean).join(' · ') }),
        e.note ? h('blockquote', { text: e.note }) : h('span'))),
    ];
  }

  private towers(towers: TowerEntry[]): HTMLElement[] {
    if (!towers.length) return [];
    return [
      h('h2', { text: 'The tallest towers, 1999' }),
      h('p', { text: 'From the original site’s table of “Top Telerobot Builders”, October to December 1999.' }),
      h('table', { class: 'landing-towers' },
        h('tr', {}, h('th', { text: 'Height' }), h('th', { text: 'Built by' }), h('th', { text: 'On' })),
        ...towers.map((t) => h('tr', {},
          h('td', { text: `${t.heightMm} mm` }), h('td', { text: t.by ?? 'a guest' }), h('td', { text: longDate(t.date) })))),
    ];
  }

  private creations(list: CreationEntry[]): HTMLElement[] {
    if (!list.length) return [];
    return [
      h('h2', { text: 'Saved from the Java applet, 2000' }),
      h('p', { text: 'Builds saved in February 2000, each with its three camera views.' }),
      ...list.map((c) => h('section', { class: 'landing-build' },
        h('h3', { text: c.title || 'Untitled' }),
        photos(c.images),
        h('p', { class: 'landing-byline', text: byline(c.by, c.date) }))),
    ];
  }
}
