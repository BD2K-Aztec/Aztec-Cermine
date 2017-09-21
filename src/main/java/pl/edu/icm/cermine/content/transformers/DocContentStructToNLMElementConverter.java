/**
 * This file is part of CERMINE project.
 * Copyright (c) 2011-2016 ICM-UW
 *
 * CERMINE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CERMINE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with CERMINE. If not, see <http://www.gnu.org/licenses/>.
 */

package pl.edu.icm.cermine.content.transformers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jdom.Element;
import pl.edu.icm.cermine.content.citations.CitationPosition;
import pl.edu.icm.cermine.content.citations.ContentStructureCitationPositions;
import pl.edu.icm.cermine.content.cleaning.ContentCleaner;
import pl.edu.icm.cermine.content.model.ContentStructure;
import pl.edu.icm.cermine.content.model.DocumentSection;
import pl.edu.icm.cermine.exception.TransformationException;
import pl.edu.icm.cermine.structure.model.BxImage;
import pl.edu.icm.cermine.tools.Pair;
import pl.edu.icm.cermine.tools.XMLTools;
import pl.edu.icm.cermine.tools.transformers.ModelToModelConverter;

/**
 * Writes DocumentContentStructure model to NLM format.
 *
 * @author Dominika Tkaczyk (d.tkaczyk@icm.edu.pl)
 */
public class DocContentStructToNLMElementConverter implements ModelToModelConverter<ContentStructure, Element> {

    @Override
    public Element convert(ContentStructure source, Object... hints) throws TransformationException {
        Element body = new Element("body");
        body.addNamespaceDeclaration(XMLTools.NS_XLINK);
        ContentStructureCitationPositions positions = null;
        List<BxImage> images = new ArrayList<BxImage>();
        for (Object hint : hints) {
            if (hint instanceof ContentStructureCitationPositions) {
                positions = (ContentStructureCitationPositions) hint;
            }
            if (hint instanceof List) {
                images = (List<BxImage>) hint;
            }
        }
        body.addContent(toHTML(source, positions, images));
        addSectionIds(body);
        return body;
    }
    
    private List<Element> toHTML(ContentStructure dcs, 
            ContentStructureCitationPositions positions, List<BxImage> images) {
        List<Element> elements = new ArrayList<Element>();
        for (BxImage image : images) {
            elements.add(toHTML(image));
        }
        for (DocumentSection part : dcs.getSections()) {
            elements.addAll(toHTML(part, positions));
        }
        return elements;
    }
    
    private Element toHTML(BxImage image) {
        Element fig = new Element("fig");
        Element graphic = new Element("graphic");
        graphic.setAttribute("href", image.getPath(), XMLTools.NS_XLINK);
        fig.addContent(graphic);
        return fig;
    }
    
    private List<Element> toHTML(DocumentSection part, ContentStructureCitationPositions positions) {
        List<Element> elements = new ArrayList<Element>();
        Element element = new Element("sec");
        element.addContent(toHTMLTitle(part.getTitle()));
        for (int i = 0; i < part.getParagraphs().size(); i++) {
//            String paragraph = part.getParagraphs().get(i);
            //add start
            String old_paragraph = part.getParagraphs().get(i);
            String paragraph = ContentCleaner.cleanAllAndBreaks(old_paragraph);
            int diff = old_paragraph.length()-paragraph.length();
            //add end
            List<Pair<Integer, CitationPosition>> positionList = new ArrayList<Pair<Integer, CitationPosition>>();
            if (positions != null) {
                positionList = positions.getPositions(part, i);
                Collections.sort(positionList, new Comparator<Pair<Integer, CitationPosition>>() {

                    @Override
                    public int compare(Pair<Integer, CitationPosition> t1, Pair<Integer, CitationPosition> t2) {
                        if (t1.getSecond().getStartRefPosition() != t2.getSecond().getStartRefPosition()) {
                            return Integer.valueOf(t1.getSecond().getStartRefPosition()).compareTo(t2.getSecond().getStartRefPosition());
                        }
                        return Integer.valueOf(t1.getSecond().getEndRefPosition()).compareTo(t2.getSecond().getEndRefPosition());
                    }
                });
            }
            element.addContent(toHTMLParagraph(paragraph, positionList,diff));
        }
        for (DocumentSection subpart : part.getSubsections()) {
            element.addContent(toHTML(subpart, positions));
        }
        elements.add(element);
        return elements;
    }

    public Element toHTMLTitle(String header) {
        Element element = new Element("title");
        element.setText(XMLTools.removeInvalidXMLChars(header+"\n"));
        return element;
    }
    
    public Element toHTMLParagraph(String paragraph, List<Pair<Integer, CitationPosition>> positions, int diff) {
        Element element = new Element("p");
        int lastParIndex = 0;
        int posIndex = 0;
        while (posIndex < positions.size()) {
            CitationPosition position = positions.get(posIndex).getSecond();
            int start = position.getStartRefPosition();
            int end = position.getEndRefPosition();
            List<Integer> citationIndices = new ArrayList<Integer>();
            citationIndices.add(positions.get(posIndex).getFirst()+1);
            while (++posIndex < positions.size() && positions.get(posIndex).getSecond().getStartRefPosition() == start) {
                citationIndices.add(positions.get(posIndex).getFirst()+1);
            }
            element.addContent(XMLTools.removeInvalidXMLChars(paragraph.substring(lastParIndex, start)));
            Element ref = new Element("xref");
            ref.setAttribute("ref-type", "bibr");
            List<String> rids = new ArrayList<String>();
            for (Integer ind : citationIndices) {
                rids.add("ref"+ind);
            }
            Collections.sort(rids);
            ref.setAttribute("rid", StringUtils.join(rids, " "));
            ref.setText(XMLTools.removeInvalidXMLChars(paragraph.substring(start-diff, end-diff)));
            element.addContent(ref);
            lastParIndex = end-diff;
        }
        element.addContent(XMLTools.removeInvalidXMLChars(paragraph.substring(lastParIndex)));
        return element;
    }
  
    private void addSectionIds(Element element) {
        List<Element> sections = element.getChildren("sec");
        int index = 1;
        for (Element section : sections) {
            addSectionIds(section, "", index++);
        }
    }
    
    private void addSectionIds(Element element, String prefix, int index) {
        if (!prefix.isEmpty()) {
            prefix += "-";
        }
        String id = prefix + index;
        element.setAttribute("id", "sec-" + id);
        List<Element> sections = element.getChildren("sec");
        int i = 1;
        for (Element section : sections) {
            addSectionIds(section, id, i++);
        }
    }

    @Override
    public List<Element> convertAll(List<ContentStructure> source, Object... hints) throws TransformationException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
