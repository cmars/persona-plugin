package hudson.plugins.persona.xml;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.plugins.persona.simple.Image;
import hudson.plugins.persona.simple.SimplePersona;

import org.dom4j.Attribute;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link SimplePersona} implementation based on XML file.
 *
 * @author Kohsuke Kawaguchi
 */
public class XmlBasedPersona extends SimplePersona {

    public final URL xml;

    private final URL imageBase;

    private final String imageBasePath;

    private String icon;

    private String displayName;

    /**
     * Parses a persona from an XML file.
     *
     * @param xml
     *      Location of the XML file.
     * @param imageBase
     *      Base URL to find images like icon.png, success.jpg, and so on.
     */
    public static XmlBasedPersona create(URL xml, URL imageBase, String imageBasePath) throws DocumentException, IOException {
        Element r = new SAXReader().read(xml).getRootElement();
        return new XmlBasedPersona(r, xml, imageBase, imageBasePath);
    }

    private XmlBasedPersona(Element r, URL xml, URL imageBase, String imageBasePath) throws IOException, DocumentException {
        super(r.attributeValue("id"), new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>());

        this.xml = xml;
        this.imageBase = imageBase;
        this.imageBasePath = imageBasePath;

        reload();
    }

    /**
     * Finds an image in ${imageBase}/${baseName}.* for some known extension
     */
    private String findImage(URL imageBase, String imageBasePath, String baseName) throws IOException {
        for (String ext : EXTENSIONS) {
            try {
                new URL(imageBase, baseName + ext).openStream().close();
                // found it.
                return imageBasePath + '/' + baseName + ext;
            } catch (IOException e) {
                // not found. try next
            }
        }
        throw new IOException("No image found that matches " + imageBase + "/" + baseName + ".*");
    }

    /**
     * Reloads the configuration of this persona from its original XML.
     */
    public void reload() throws IOException, DocumentException {
        this.icon = findImage(imageBase, imageBasePath, "icon");

        Element r = new SAXReader().read(xml).getRootElement();
        this.displayName = r.attributeValue("displayName");

        imagesSuccess = new ArrayList<String>();
        imagesFailure = new ArrayList<String>();
        imagesOther = new ArrayList<String>();
        
        List<String> quotes = new ArrayList<String>();
        List<String> quotesSuccess = new ArrayList<String>();
        List<String> quotesFailure = new ArrayList<String>();
        List<String> quotesOther = new ArrayList<String>();
        
        for (Element e : (List<Element>) r.elements("image")) {
            Attribute attribute = e.attribute("type");
            String imagePath = e.getTextTrim();

            if (null == attribute || null == imagePath) {
                continue;
            }
            
            imagePath = imageBasePath + '/' + imagePath;
            
            if ("success".equalsIgnoreCase(attribute.getText())) {
                imagesSuccess.add(imagePath);
            } else if ("failure".equalsIgnoreCase(attribute.getText())) {
            	imagesFailure.add(imagePath);
            } else if ("other".equalsIgnoreCase(attribute.getText())) {
            	imagesOther.add(imagePath);
            }
        }
        
        for (Element e : (List<Element>) r.elements("quote")) {
            Attribute attribute = e.attribute("type");
            String quote = e.getTextTrim();

            if (null == attribute) {
                quotes.add(quote);
            } else if ("success".equalsIgnoreCase(attribute.getText())) {
                quotesSuccess.add(quote);
            } else if ("failure".equalsIgnoreCase(attribute.getText())) {
                quotesFailure.add(quote);
            } else if ("other".equalsIgnoreCase(attribute.getText())) {
                quotesOther.add(quote);
            } else {
                // unrecognized attribute type, use default quote
                quotes.add(quote);
            }
        }
        setQuotes(quotes, quotesSuccess, quotesFailure, quotesOther);
    }

    @Override
    public Image getImage(AbstractBuild<?, ?> build) {
        Result r = build.getResult();
        if (r == Result.SUCCESS) {
        	String success = chooseRandomFrom(imagesSuccess);
            return new Image(icon, success);
        }
        if (r == Result.FAILURE) {
        	String failure = chooseRandomFrom(imagesFailure);
            return new Image(icon, failure);
        }
    	String other = chooseRandomFrom(imagesOther);
        return new Image(icon, other);
    }

    private String chooseRandomFrom(List<String> images) {
    	if (images == null || images.isEmpty()) {
    		return null;
    	}
    	
    	List<String> deck = new ArrayList<String>(images);
    	Collections.shuffle(deck, new SecureRandom());
		return deck.get(0);
	}

	@Override
    public Image getDefaultImage() {
        return new Image(icon, chooseRandomFrom(imagesSuccess));
    }

    public String getDisplayName() {
        return displayName;
    }

    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif",
        ".JPG", ".JPEG", ".PNG", ".GIF"};

	private List<String> imagesSuccess;

	private List<String> imagesFailure;

	private List<String> imagesOther;
}
