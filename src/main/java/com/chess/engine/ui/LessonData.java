package com.chess.engine.ui;

import java.util.ArrayList;
import java.util.List;

/* The text and diagrams of the "Chess Principles" course. */
public class LessonData {

    static final class Diagram {
        final MiniBoard.Mode mode;
        final String caption;
        final String fen;
        final String[] moves;
        final String marks;
        final String arrows;

        private Diagram(MiniBoard.Mode mode, String caption, String fen, String[] moves, String marks, String arrows) {
            this.mode = mode;
            this.caption = caption;
            this.fen = fen;
            this.moves = moves;
            this.marks = marks;
            this.arrows = arrows;
        }

        /** Free play: click a piece to see its moves. */
        static Diagram explore(String caption, String fen, String marks) {
            return new Diagram(MiniBoard.Mode.EXPLORE, caption, fen, new String[0], marks, null);
        }

        /** A prepared sequence, stepped through with the arrow buttons. */
        static Diagram line(String caption, String fen, String uciMoves, String marks) {
            return new Diagram(MiniBoard.Mode.LINE, caption, fen, uciMoves.trim().split("\\s+"), marks, null);
        }

        /** A static diagram with highlighted squares and arrows. */
        static Diagram fixed(String caption, String fen, String marks, String arrows) {
            return new Diagram(MiniBoard.Mode.FIXED, caption, fen, new String[0], marks, arrows);
        }
    }

    static final class Lesson {
        final String section;
        final String title;
        final String html;
        final Diagram[] diagrams;

        Lesson(String section, String title, String html, Diagram... diagrams) {
            this.section = section;
            this.title = title;
            this.html = html;
            this.diagrams = diagrams;
        }
    }

    static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static List<Lesson> all;

    private LessonData() { }

    static synchronized List<Lesson> all() {
        if (all == null) {
            List<Lesson> l = new ArrayList<>();
            basics(l);
            openings(l);
            tactics(l);
            strategy(l);
            middlegame(l);
            endgames(l);
            all = l;
        }
        return all;
    }

    // 1. The basics

    private static void basics(List<Lesson> l) {
        String s = "1. The Basics";
        l.add(new Lesson(s, "The board and the starting position",
            "<p>Chess is played on an 8×8 board of 64 squares, alternating light and dark. Each player starts with 16 pieces: "
            + "one king, one queen, two rooks, two bishops, two knights and eight pawns.</p>"
            + "<p>Set the board up so that every player has a <b>light square at the bottom-right corner</b>. "
            + "The queen always starts on her own colour: the white queen on a light square (d1), the black queen on a dark square (d8).</p>"
            + "<p>Files are the columns (a–h), ranks are the rows (1–8). Every square has a name made from its file and rank, "
            + "such as <b>e4</b>. White moves first, and then the players alternate; you may never skip a move.</p>"
            + "<p>The goal is to <b>checkmate</b> the opposing king: attack it so that it cannot escape.</p>",
            Diagram.fixed("The starting position. The files a–h run left to right, the ranks 1–8 bottom to top.", START, "a1 h8", null)));

        l.add(new Lesson(s, "How the pieces move",
            "<p>Try each piece on the boards on the right: click it to see where it can go, then click a highlighted square.</p>"
            + "<ul><li><b>King</b> – one square in any direction. It can never move to a square attacked by the enemy.</li>"
            + "<li><b>Queen</b> – any number of squares along a rank, file or diagonal.</li>"
            + "<li><b>Rook</b> – any number of squares along a rank or file.</li>"
            + "<li><b>Bishop</b> – any number of squares diagonally; it stays on its colour for the whole game.</li>"
            + "<li><b>Knight</b> – an “L”: two squares one way and one square at a right angle. It is the only piece that jumps over others.</li>"
            + "<li><b>Pawn</b> – forward one square (two from its starting square), but it captures one square diagonally forward.</li></ul>"
            + "<p>Every piece except the pawn captures the way it moves, by landing on the enemy piece.</p>",
            Diagram.explore("King: one step in any direction.", "7k/8/8/3K4/8/8/8/8 w - - 0 1", ""),
            Diagram.explore("Queen: rook + bishop.", "7k/8/8/3Q4/8/8/8/K7 w - - 0 1", ""),
            Diagram.explore("Rook: ranks and files.", "7k/8/8/3R4/8/8/8/K7 w - - 0 1", ""),
            Diagram.explore("Bishop: diagonals only.", "7k/8/8/3B4/8/8/8/K7 w - - 0 1", ""),
            Diagram.explore("Knight: an L-shaped jump.", "7k/8/8/3N4/8/8/8/K7 w - - 0 1", ""),
            Diagram.explore("Pawn: moves straight, captures diagonally. Try e4xd5 and e4xf5.", "7k/8/8/3p1p2/4P3/8/8/K7 w - - 0 1", ""),
            Diagram.explore("Pawn: two squares from its starting square.", "7k/8/8/8/8/8/4P3/K7 w - - 0 1", "")));

        l.add(new Lesson(s, "Castling, en passant, promotion",
            "<p><b>Castling</b> moves the king two squares towards a rook and puts that rook on the other side of the king. "
            + "It is allowed only if neither piece has moved before, no pieces stand between them, the king is not in check, "
            + "and the king does not pass over or land on an attacked square. It tucks the king away and activates the rook – "
            + "usually a good idea early in the game.</p>"
            + "<p><b>En passant</b>: if a pawn advances two squares and lands beside an enemy pawn, that pawn may capture it as if it had moved only one square. "
            + "The option exists for that single move only.</p>"
            + "<p><b>Promotion</b>: a pawn that reaches the last rank must become a queen, rook, bishop or knight (almost always a queen). "
            + "This is why advanced pawns are so dangerous.</p>",
            Diagram.explore("Castling: click the white king and choose g1 or c1.", "r3k2r/pppqbppp/2n1bn2/3pp3/3PP3/2N1BN2/PPPQBPPP/R3K2R w KQkq - 0 1", "e1 g1 c1"),
            Diagram.line("En passant: after ...d5 the white pawn takes on d6 as if the pawn had moved one square.", "4k3/3p4/8/4P3/8/8/8/4K3 b - - 0 1", "d7d5 e5d6", "d6"),
            Diagram.explore("Promotion: push the pawn to e8 (in this trainer it becomes a queen).", "7k/4P3/8/8/8/8/8/K7 w - - 0 1", "e8")));

        l.add(new Lesson(s, "Check, checkmate and stalemate",
            "<p>A king under attack is <b>in check</b>. You must get out of check at once by moving the king, blocking the attack, or capturing the attacker. "
            + "You may never make a move that leaves your own king in check.</p>"
            + "<p>If you are in check and no move helps, it is <b>checkmate</b> and the game is lost.</p>"
            + "<p>If you are <i>not</i> in check but have no legal move, it is <b>stalemate</b> and the game is a draw. "
            + "Beware: when you are far ahead, always leave the opponent a legal move!</p>",
            Diagram.fixed("Check: the rook attacks the king on e8.", "4k3/8/8/8/8/8/4R3/4K3 b - - 0 1", "e8", "e2e8"),
            Diagram.line("Checkmate: Ra8 is mate – the king is boxed in by its own pawns (a “back-rank mate”).", "6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1", "a1a8", "g8"),
            Diagram.fixed("Stalemate: Black to move has no legal move but is not in check – a draw.", "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", "h8", null)));

        l.add(new Lesson(s, "Draws and the value of the pieces",
            "<p>A game can end in a draw by <b>stalemate</b>, <b>agreement</b>, <b>threefold repetition</b> of the same position, "
            + "the <b>fifty-move rule</b> (fifty moves by each side without a capture or pawn move), or <b>insufficient material</b> to checkmate "
            + "(for example king against king, or king and one minor piece against a king).</p>"
            + "<p>To judge trades, use the traditional values: <b>pawn 1, knight 3, bishop 3, rook 5, queen 9</b>. The king is priceless. "
            + "Trading a knight for three pawns or a rook for a bishop and a pawn is therefore about even, but giving a rook (5) for a knight (3) loses material. "
            + "Two bishops are usually a little more valuable than their 6 points suggest.</p>"
            + "<p>Values are only a guide: an attack on the king, an advanced passed pawn or a mating pattern can be worth much more than material.</p>",
            Diagram.fixed("Pawn = 1, knight = bishop = 3, rook = 5, queen = 9.", START, "", null)));
    }

    // 2. Openings

    private static void openings(List<Lesson> l) {
        String s = "2. The Opening";
        l.add(new Lesson(s, "Ten opening principles",
            "<ol><li><b>Fight for the centre</b> (e4, d4, e5, d5) with pawns and pieces.</li>"
            + "<li><b>Develop your pieces</b> – knights and bishops first, towards the centre.</li>"
            + "<li><b>Castle early</b> to protect the king and connect the rooks.</li>"
            + "<li><b>Don't move the same piece twice</b> without a good reason.</li>"
            + "<li><b>Don't bring the queen out too early</b>; it can be chased by smaller pieces.</li>"
            + "<li><b>Knights before bishops</b> – it's easier to see where the knights belong.</li>"
            + "<li><b>Don't make too many pawn moves</b>; every pawn move is a piece not developed.</li>"
            + "<li><b>Watch for the opponent's threats</b> after every move – ask “what does that move attack?”.</li>"
            + "<li><b>Connect the rooks</b> by clearing the back rank, then put them on open files.</li>"
            + "<li><b>Have a plan</b>; don't just make random moves.</li></ol>",
            Diagram.fixed("A model set-up: pawns in the centre, pieces developed, ready to castle.",
                "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", "e4 e5 d4 d5 c4 c5 f3 c6", null)));

        l.add(new Lesson(s, "Scholar's mate and how to avoid it",
            "<p>Beginners often try the fastest checkmate: queen and bishop against f7 (f2 for Black), the weakest point because only the king protects it.</p>"
            + "<p>Step through the trap. To defend, simply respond to 3.Qh5 with ...g6 or ...Qe7, or even better develop your knight to f6 in time to attack the queen.</p>"
            + "<p>The lesson for the attacker, too: an early queen sortie wins only against careless opponents; against good defence you've simply lost time.</p>",
            Diagram.line("1.e4 e5 2.Bc4 Nc6 3.Qh5? Nf6?? 4.Qxf7# – mate.",
                START, "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7", "f7"),
            Diagram.line("Correct defence: 3...g6! hits the queen. The queen loses time.",
                START, "e2e4 e7e5 f1c4 b8c6 d1h5 g7g6 h5f3 g8f6", "g6")));

        l.add(new Lesson(s, "Open games: Italian and Ruy Lopez",
            "<p>After <b>1.e4 e5</b> the game is “open” – files open quickly and tactics arise early. White develops with <b>2.Nf3</b>, attacking e5, and Black replies <b>2...Nc6</b> to defend it.</p>"
            + "<p>The <b>Italian Game</b> (3.Bc4) aims the bishop at f7 and prepares c3 and d4 to take the centre. "
            + "The <b>Ruy Lopez</b> (3.Bb5) pressures the knight that defends e5, building long-term pressure. "
            + "Both are classical, sound openings, still played at the highest level.</p>",
            Diagram.line("Italian Game: Bc4, then c3 and d3/d4, castling early.", START,
                "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6 e1g1", "c4 f7"),
            Diagram.line("Ruy Lopez: 3.Bb5 a6 4.Ba4 Nf6 5.O-O Be7 6.Re1 b5 7.Bb3 d6 8.c3 O-O.", START,
                "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6 c2c3 e8g8", "b5 c6")));

        l.add(new Lesson(s, "Semi-open and closed openings",
            "<p>The <b>Sicilian Defence</b> (1.e4 c5) is Black's most popular, ambitious reply: Black fights for d4 with a wing pawn, "
            + "creating an asymmetric position where both sides can play for a win.</p>"
            + "<p>The <b>French Defence</b> (1.e4 e6) is solid: Black builds a pawn chain and counterattacks the white centre with ...c5 and ...f6. "
            + "The light-squared bishop is its problem piece.</p>"
            + "<p>The <b>Queen's Gambit</b> (1.d4 d5 2.c4) offers a pawn to deflect Black's central pawn; if Black takes, White gets the centre and the pawn is soon regained. "
            + "In the Queen's Gambit Declined (2...e6) Black keeps a firm centre.</p>",
            Diagram.line("Sicilian Defence (Open): White opens the centre, Black gets queenside counterplay.", START,
                "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6", "c5 d4"),
            Diagram.line("French Defence, Classical: pawn chain on d5/e6, tension in the centre.", START,
                "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7 e4e5 f6d7 g5e7 d8e7", "d5 e6"),
            Diagram.line("Queen's Gambit Declined: Black keeps d5 and develops solidly.", START,
                "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7 e2e3 e8g8 g1f3", "c4 d5")));
    }

    // 3. Tactics

    private static void tactics(List<Lesson> l) {
        String s = "3. Tactics";
        l.add(new Lesson(s, "Forks",
            "<p>A <b>fork</b> is a single move that attacks two or more enemy pieces at once; the opponent can save only one. "
            + "Knights are the great forkers because their attack pattern is so unusual, but queens, pawns and even kings can fork too.</p>"
            + "<p>Before every move, look at the squares your knight can reach: does any of them attack two things? "
            + "And look at your opponent's too – “does my last move allow a fork?”</p>",
            Diagram.line("A royal fork: Nc7+ attacks the king and the rook on a8.", "r3k3/8/4N3/8/8/8/PPP5/4K3 w - - 0 1", "e6c7 e8d8 c7a8", "c7 a8 e8")));

        l.add(new Lesson(s, "Pins",
            "<p>A <b>pin</b> is an attack on a piece that cannot (or should not) move because a more valuable piece stands behind it. "
            + "If the piece behind is the king, it is an <i>absolute pin</i>: the pinned piece cannot legally move. "
            + "If it is something else, it is a <i>relative pin</i>: moving is legal but costs material.</p>"
            + "<p>Pinned pieces are weak: pile up attackers on them. Only bishops, rooks and queens can pin.</p>",
            Diagram.explore("The knight on e7 is pinned to its king – try to move it!", "4k3/4n3/8/8/8/8/4R3/4K3 b - - 0 1", "e7 e8"),
            Diagram.line("A relative pin: Bg5 pins the knight to the queen.", START, "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5", "f6 d8")));

        l.add(new Lesson(s, "Skewers",
            "<p>A <b>skewer</b> is the reverse of a pin: the more valuable piece is attacked first, must move, and exposes the less valuable piece behind it, which is then captured.</p>",
            Diagram.line("Ra8+ drives the king away and wins the queen on h8.", "4k2q/8/8/8/8/8/8/R3K3 w - - 0 1", "a1a8 e8d7 a8h8", "a8 e8 h8")));

        l.add(new Lesson(s, "Discovered attacks",
            "<p>A <b>discovered attack</b> occurs when you move one piece and thereby uncover an attack by another piece behind it. "
            + "The moving piece can itself make a threat at the same time, which is what makes the motif so strong. "
            + "A discovered <i>check</i> is the most powerful form: the opponent must answer the check and cannot save the other target.</p>",
            Diagram.line("Nc6+! discovers the rook's check and attacks the queen; after ...Qe7 Nxe7 wins it (the knight is protected by the rook).", "3qk3/8/8/4N3/8/8/8/4R1K1 w - - 0 1", "e5c6 d8e7 c6e7", "e5 e1 d8")));

        l.add(new Lesson(s, "Mating patterns",
            "<p>The <b>back-rank mate</b> hits a king that is trapped behind its own pawns. Give your king “luft” (a flight square) with h3/h6 or g3/g6, "
            + "and look for the same chance against your opponent whenever his back rank is thin.</p>"
            + "<p>The <b>queen and bishop battery</b> aims a queen backed by a bishop at a king's pawn cover: Qxg7 is mate because the queen is protected. "
            + "The <b>smothered mate</b> is the knight's masterpiece: the king is hemmed in by its own pieces and a knight gives the final check.</p>",
            Diagram.line("Back-rank mate: the rook delivers check on the eighth rank.", "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "a1a8", "a8"),
            Diagram.line("Battery: the queen takes g7 protected by the bishop on b2.", "6k1/5ppp/8/8/3Q4/8/1B6/6K1 w - - 0 1", "d4g7", "b2 d4"),
            Diagram.fixed("Smothered mate: the king on h8 is blocked in by its own rook and pawns; Nf7 is checkmate.", "6rk/5Npp/8/8/8/8/8/6K1 b - - 0 1", "f7 h8", null)));

        l.add(new Lesson(s, "How to find tactics",
            "<p>A simple routine before every move: <b>checks, captures, threats</b>. Look at every check, every capture and every threat – first yours, then your opponent's. "
            + "Most games below master level are decided by a tactic one side missed.</p>"
            + "<ul><li>Which enemy pieces are <b>undefended</b> or defended only once?</li>"
            + "<li>Is any of your pieces loose? <b>Loose pieces drop off</b>.</li>"
            + "<li>Is the enemy king exposed? Can a sacrifice open it?</li>"
            + "<li>Before you move, ask: “if I play this, what is the opponent's most dangerous reply?”</li></ul>"
            + "<p>Practise: the Puzzles section of this program trains exactly these skills.</p>"));
    }

    //  4. Strategy

    private static void strategy(List<Lesson> l) {
        String s = "4. Strategy";
        l.add(new Lesson(s, "Pawn structure",
            "<p>Pawns cannot move backwards, so every pawn move changes the position permanently. Know the key structures:</p>"
            + "<ul><li><b>Passed pawn</b>: no enemy pawn in front or on the adjacent files – a major asset, especially in the endgame (“passed pawns must be pushed”).</li>"
            + "<li><b>Isolated pawn</b>: no friendly pawn on adjacent files – it must be defended by pieces and the square in front is a good blockade square.</li>"
            + "<li><b>Doubled pawns</b>: two pawns on one file – usually a weakness, though they may give open files.</li>"
            + "<li><b>Backward pawn</b>: behind its neighbours and unable to advance safely.</li>"
            + "<li><b>Pawn chain</b>: pawns protecting each other diagonally – attack the base.</li></ul>",
            Diagram.fixed("White's d5 pawn is passed; the f-pawns on f3 and f2 are doubled.", "4k3/pp3ppp/8/3P4/8/2P2P2/PP3PPP/4K3 w - - 0 1", "d5 f3 f2", null)));

        l.add(new Lesson(s, "Outposts and good knights",
            "<p>An <b>outpost</b> is a square in the enemy half of the board, protected by one of your pawns, that no enemy pawn can ever attack. A knight on an outpost is often stronger than a rook.</p>"
            + "<p>The classic outposts are d5/e5/c5 (for White) and d4/e4/c4 (for Black). Look for squares like that: they tell you where to route your knights.</p>",
            Diagram.fixed("The knight on d5 can never be driven away by a pawn: Black has no c- or e-pawn.", "4k3/pp3ppp/8/3N4/2P1P3/8/PP3PPP/4K3 w - - 0 1", "d5", null)));

        l.add(new Lesson(s, "Open files and the seventh rank",
            "<p>Rooks need open lines. A <b>file</b> is open when there are no pawns on it; <b>half-open</b> when only the opponent's pawn is on it. Put your rooks on open files and double them.</p>"
            + "<p>A rook on the <b>seventh rank</b> (second for Black) attacks pawns from the side and cramps the king – “a rook on the seventh is worth a pawn”. "
            + "A rook that reaches the opponent's back rank through an open file often wins the game.</p>",
            Diagram.fixed("Both sides want to occupy the open d-file; the first to invade usually gains an advantage.", "3rk3/ppp2ppp/8/8/8/8/PPP2PPP/3RK3 w - - 0 1", "d1 d8", "d1d8")));

        l.add(new Lesson(s, "Bishops: the pair and the “bad” bishop",
            "<p>Two bishops cover both colours and work superbly in open positions – the <b>bishop pair</b> is worth about half a pawn. "
            + "Knights prefer closed positions with outposts; bishops prefer open boards and long diagonals.</p>"
            + "<p>A <b>bad bishop</b> is one whose own pawns stand on the same colour as the bishop, blocking it. "
            + "Keep your pawns on the opposite colour of your bishop, or trade the bad bishop for a knight.</p>",
            Diagram.fixed("White's light-squared bishop is shut in by his own pawns on light squares (b3, c4, e4, g4).", "4k3/8/8/8/2P1P1P1/1P6/8/4KB2 w - - 0 1", "f1 b3 c4 e4 g4", null)));

        l.add(new Lesson(s, "King safety",
            "<p>In the opening and middlegame the king belongs in a corner behind its pawns: castle early and avoid weakening the pawn cover "
            + "(moving f, g and h pawns carelessly leaves holes). A king stuck in the centre when files open is the target of most attacks.</p>"
            + "<p>Once queens and most pieces are traded, the king becomes a strong piece: bring it towards the centre in the endgame.</p>",
            Diagram.fixed("Both sides are castled behind intact f-, g- and h-pawns.", "r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 1", "g1 f2 g2 h2 g8 f7 g7 h7", null)));

        l.add(new Lesson(s, "Space, activity and weak squares",
            "<p>The side with more <b>space</b> has more room to manoeuvre; the cramped side should trade pieces. "
            + "<b>Activity</b> is more important than material in many positions: a rook on an open file beats a passive rook, and a centralised knight beats a rim knight (“a knight on the rim is dim”).</p>"
            + "<p>A <b>weak square</b> is one that can no longer be defended by a pawn. Occupying it with a piece, ideally a knight, gives you a lasting edge. "
            + "Create weak squares in the opponent's camp by provoking pawn moves, and avoid creating them in yours.</p>"));
    }

    //  5. Middlegame

    private static void middlegame(List<Lesson> l) {
        String s = "5. Middlegame Ideas";
        l.add(new Lesson(s, "Making a plan",
            "<p>The middlegame starts when development is complete. Beginners drift; strong players always have a <b>plan</b>. To find one, assess the position:</p>"
            + "<ol><li><b>King safety</b> for both sides.</li><li><b>Material</b> and piece activity.</li>"
            + "<li><b>Pawn structure</b>: where are the weaknesses and the open lines?</li>"
            + "<li><b>Pieces</b>: which is your worst piece, and how can it be improved?</li></ol>"
            + "<p>Then choose a goal: attack a weakness, open a file, create a passed pawn, or attack the king. "
            + "Example: in this typical Carlsbad structure White's classic plan is the <b>minority attack</b> – pushing the b-pawn (b4–b5) to create a weakness on c6.</p>",
            Diagram.fixed("Minority attack: White advances b2-b4-b5 against Black's c6 pawn.", "rnbq1rk1/pp2bppp/2p1pn2/3p4/2PP4/2NBPN2/PP3PPP/R1BQ1RK1 w - - 0 1", "b2 c6", "b2b4")));

        l.add(new Lesson(s, "Attacking the king",
            "<p>Attack when you have <b>more pieces near the enemy king</b> than he has defenders, and when the centre is stable (a king attack on a flank is best answered by a central counter-blow).</p>"
            + "<ul><li>Open lines near the king with pawn breaks or sacrifices.</li>"
            + "<li>Bring every piece into the attack, especially the queen, a bishop aimed at the king and a knight on f5 or g5.</li>"
            + "<li>Calculate carefully – an unsound sacrifice loses the game.</li>"
            + "<li>If there is no clear attack, don't force it: improve your position first.</li></ul>",
            Diagram.line("A queen and bishop battery on the long diagonal ends the game: Qxg7#.", "6k1/5ppp/8/8/3Q4/8/1B6/6K1 w - - 0 1", "d4g7", "b2 g7")));

        l.add(new Lesson(s, "Exchanges, prophylaxis, initiative",
            "<p><b>When to trade pieces:</b> trade when you are ahead in material (it simplifies), when you are cramped, or when you can trade off the opponent's best piece. "
            + "Avoid trades when you are attacking or when the opponent has a bad piece you'd rather keep him with.</p>"
            + "<p><b>Prophylaxis</b>: before making your own plan, ask “what does my opponent want to do?” and, if necessary, prevent it. "
            + "A quiet move that stops the opponent's best idea is often the strongest.</p>"
            + "<p><b>The initiative</b>: the side that makes threats forces the other to respond. Keep making threats while you have the initiative; don't give it up for a small material gain.</p>"));

        l.add(new Lesson(s, "Thinking habits that win games",
            "<ol><li><b>Blunder check</b>: before every move, look at what your move leaves undefended and what the opponent's checks, captures and threats are.</li>"
            + "<li><b>Always ask why</b> the opponent made his last move.</li>"
            + "<li><b>Improve your worst piece</b> when nothing is urgent.</li>"
            + "<li><b>Don't rush</b> – when ahead, play safe; when behind, make it complicated.</li>"
            + "<li><b>Calculate forcing lines</b> (checks, captures, threats) to the end.</li>"
            + "<li><b>Learn from your games</b>: replay them and find the moment you went wrong.</li></ol>"));
    }

    //  6. Endgames

    private static void endgames(List<Lesson> l) {
        String s = "6. Endgame Ideas";
        l.add(new Lesson(s, "Endgame principles",
            "<p>In the endgame the king is a fighter: <b>centralise it</b> as soon as queens are off the board. "
            + "Other general principles: create a passed pawn, use your king to support your pawns, keep your rooks active, "
            + "and count tempo: in a pawn race, count the moves.</p>"
            + "<p>Remember: <b>“the endgame is about technique”</b> – learn the basic positions below and you will convert many half-points into full ones.</p>",
            Diagram.fixed("Both kings should head for the centre: Kf3-e4 is the natural plan.", "8/pp3k2/2p5/8/8/2P2K2/PP6/8 w - - 0 1", "f3 e4", "f3e4")));

        l.add(new Lesson(s, "King and pawn: opposition",
            "<p>Two kings stand in <b>opposition</b> when they are on the same file or rank with one square between them. The side that does <i>not</i> have to move holds the opposition, "
            + "and the other king must give way. A king in front of its pawn, with the opposition, wins; without it the game is often drawn.</p>"
            + "<p>Rule of thumb: a king on the sixth rank in front of its pawn (on the fifth) wins, whoever is to move.</p>",
            Diagram.line("White wins: the king goes round with Kd6, the pawn advances with support and promotes.", "4k3/8/4K3/4P3/8/8/8/8 w - - 0 1", "e6d6 e8d8 e5e6 d8e8 e6e7 e8f7 d6d7", "e6 e5")));

        l.add(new Lesson(s, "The rule of the square",
            "<p>Can the king catch a passed pawn? Draw a square whose side equals the number of moves the pawn needs to promote, starting from the pawn. "
            + "If the defending king can step into that square on his move, he catches the pawn; if he is outside, the pawn queens.</p>",
            Diagram.line("The black king on h6 is outside the square; the pawn promotes.", "8/8/7k/8/2P5/8/8/K7 w - - 0 1", "c4c5 h6g7 c5c6 g7f7 c6c7 f7e7 c7c8q",
                "c4 d4 e4 f4 g4 c5 g5 c6 g6 c7 g7 c8 d8 e8 f8 g8")));

        l.add(new Lesson(s, "Rook endgames: the golden rules",
            "<p>Rook endings are the most common endgames. The essentials:</p>"
            + "<ul><li><b>Rooks belong behind passed pawns</b> – yours or the opponent's.</li>"
            + "<li><b>Cut the enemy king off</b> along a rank or file.</li>"
            + "<li><b>Active rooks beat passive ones</b>: an extra pawn is often not enough if your rook must sit passively – be active.</li>"
            + "<li>The <b>Philidor position</b>: the defender keeps his rook on the sixth rank until the pawn advances, then goes behind it to give checks – a draw.</li>"
            + "<li>The <b>Lucena position</b> (king in front of a pawn on the seventh with the enemy king cut off) is a win by “building a bridge” with the rook.</li></ul>",
            Diagram.fixed("Philidor: Black keeps the rook on the sixth rank so the white king cannot advance. It is a draw.", "3k4/8/r7/3PK3/8/8/8/1R6 b - - 0 1", "a6 b6 c6 d6 e6 f6 g6 h6", null),
            Diagram.fixed("Rook behind the passed pawn: it supports the advance from behind and stays active.", "4k3/8/8/P7/8/8/8/R3K3 w - - 0 1", "a1 a5", "a1a5")));

        l.add(new Lesson(s, "Basic checkmates",
            "<p>You must know how to mate with king and queen, and king and rook, against a lone king:</p>"
            + "<ul><li><b>Queen</b>: confine the king with queen moves a knight's move away, bring your king up, finish with Qg7#-type mates next to the king. "
            + "Watch for stalemate!</li>"
            + "<li><b>Rook</b>: drive the king to the edge, bring your king in opposition, and give mate on the edge with the rook.</li>"
            + "<li><b>Two rooks</b>: the “ladder” – one rook cuts off a rank, the other checks, then they swap.</li></ul>",
            Diagram.line("Ladder mate with two rooks: Rb7 cuts off the seventh rank, Ra8 is checkmate.", "6k1/8/8/8/8/8/1R6/R5K1 w - - 0 1", "b2b7 g8h8 a1a8", "b7 a8"),
            Diagram.explore("Practise: mate the black king with the queen and king. Careful about stalemate!", "8/8/8/4k3/8/8/8/K2Q4 w - - 0 1", "")));

        l.add(new Lesson(s, "Zugzwang, fortresses, wrong bishop",
            "<p><b>Zugzwang</b> (German: “move compulsion”) is a position in which any move worsens the position, and it happens often in king and pawn endgames: the side that has to move loses.</p>"
            + "<p>The <b>wrong-coloured bishop</b>: a bishop and a rook pawn (a- or h-pawn) draw if the bishop cannot control the promotion square and the defending king reaches the corner. "
            + "Here the pawn promotes on a8, a light square, but the white bishop is dark-squared.</p>"
            + "<p>The lesson: when you're worse, look for a <b>fortress</b> or a stalemate trick; when you're better, check that your opponent doesn't have one.</p>",
            Diagram.fixed("The bishop is the wrong colour for the a8 corner; the game is a draw.", "8/k7/8/P7/8/8/8/2B1K3 w - - 0 1", "a8 c1 a5", null)));
    }
}