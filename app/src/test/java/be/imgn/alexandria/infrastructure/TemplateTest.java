package be.imgn.alexandria.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TemplateTest {

    @Test
    void fillsSlotsByName() {
        String page = Template.of("<h1>{title}</h1><p>{byline}</p>")
                .with("title", "Don Quixote")
                .with("byline", "Miguel de Cervantes")
                .render();

        assertThat(page).isEqualTo("<h1>Don Quixote</h1><p>Miguel de Cervantes</p>");
    }

    @Test
    void escapesWhatItInserts() {
        String page = Template.of("<p>{note}</p>")
                .with("note", "<script>alert(1)</script> & \"quoted\"")
                .render();

        assertThat(page)
                .doesNotContain("<script>")
                .isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quoted&quot;</p>");
    }

    @Test
    void leavesMarkupAloneWhenAskedTo() {
        String page = Template.of("<div>{body}</div>")
                .withMarkup("body", "<a href=\"/works/x\">A title</a>")
                .render();

        assertThat(page).isEqualTo("<div><a href=\"/works/x\">A title</a></div>");
    }

    @Test
    void fillsTheSameSlotEverywhereItAppears() {
        String page = Template.of("<a href=\"{root}/a.css\">x</a><script src=\"{root}/b.js\"></script>")
                .with("root", "..")
                .render();

        assertThat(page).isEqualTo("<a href=\"../a.css\">x</a><script src=\"../b.js\"></script>");
    }

    /** The failure mode this class exists to prevent: a slot nobody filled, silently rendering blank. */
    @Test
    void refusesToRenderWithASlotLeftEmpty() {
        assertThatThrownBy(() -> Template.of("<h1>{title}</h1><p>{byline}</p>")
                        .with("title", "Don Quixote")
                        .render())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("byline");
    }

    /** And the other direction: a value whose slot was renamed or never existed. */
    @Test
    void refusesAValueThatMatchesNoSlot() {
        assertThatThrownBy(() -> Template.of("<h1>{title}</h1>").with("titel", "typo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titel")
                .hasMessageContaining("title");
    }

    @Test
    void leavesBracesThatAreNotSlotsAlone() {
        String page = Template.of("<style>.a { color: red } </style><p>{note}</p>")
                .with("note", "kept")
                .render();

        assertThat(page).isEqualTo("<style>.a { color: red } </style><p>kept</p>");
    }

    /** A replacement carrying $ or \ must not be read as a regex backreference. */
    @Test
    void treatsTheValueAsTextNotAsAReplacementPattern() {
        String page = Template.of("<p>{price}</p>").with("price", "$5 \\ 10").render();

        assertThat(page).isEqualTo("<p>$5 \\ 10</p>");
    }

    @Test
    void writesNumbersWithoutEscaping() {
        assertThat(Template.of("<p>{count} works</p>").with("count", 12).render())
                .isEqualTo("<p>12 works</p>");
    }
}
