package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword search over the catalogue.
 *
 * Matching and relevance ordering live in ProductMapper.selectWithFilters;
 * this adds the matched-term highlighting the item cards render. Keeping the
 * highlighting here rather than in SQL means swapping the matching engine for
 * Elasticsearch later only replaces the query, not the presentation.
 */
@Service
public class SearchService {

    public List<ProductDTO> highlight(List<ProductDTO> results, String keyword) {
        if (keyword == null || keyword.isBlank() || results == null) {
            return results;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(keyword.strip()), Pattern.CASE_INSENSITIVE);
        for (ProductDTO dto : results) {
            dto.setHighlightedName(mark(dto.getName(), pattern));
            dto.setHighlightedDescription(mark(snippet(dto.getDescription(), pattern), pattern));
        }
        return results;
    }

    private String mark(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll(match -> "<mark>" + Matcher.quoteReplacement(match.group()) + "</mark>");
    }

    /** Show the text around the first hit rather than the whole description. */
    private String snippet(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text.length() <= 120 ? text : text.substring(0, 120) + "...";
        }
        int start = Math.max(0, matcher.start() - 40);
        int end = Math.min(text.length(), matcher.end() + 80);
        return (start > 0 ? "..." : "") + text.substring(start, end) + (end < text.length() ? "..." : "");
    }
}
