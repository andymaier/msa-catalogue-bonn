package de.predi8.catalogue.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.predi8.catalogue.error.NotFoundException;
import de.predi8.catalogue.event.Operation;
import de.predi8.catalogue.model.Article;
import de.predi8.catalogue.repository.ArticleRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/articles")
public class CatalogueRestController {

  public static final String PRICE = "price";
  public static final String NAME = "name";
  private ArticleRepository repo;
  private KafkaTemplate<String, Operation> kafka;
  final private ObjectMapper mapper;

  public CatalogueRestController(ArticleRepository repo, KafkaTemplate<String, Operation> kafka, ObjectMapper mapper) {
    this.repo = repo;
    this.kafka = kafka;
    this.mapper = mapper;
  }

  @GetMapping
  public List<Article> index() {
    return repo.findAll();
  }

  @GetMapping("/count")
  public long count() {
    return repo.count();
  }

  @PostMapping
  public ResponseEntity<Article> create(@RequestBody Article article, UriComponentsBuilder builder) {
    System.out.println("article = " + article);

    String uuid = UUID.randomUUID().toString();
    article.setUuid(uuid);

    Operation op = new Operation("article", "upsert", mapper.valueToTree(article));
    kafka.send(new ProducerRecord<>("shop", op));

    //das erfolgt wo anders
    //repo.save(article);
    return ResponseEntity.created(builder.path("/articles/" + uuid).build().toUri()).body(article);
  }

  @GetMapping("/{id}")
  public Article get(@PathVariable String id) {
    return repo.findById(id).orElseThrow(() -> {
      return new NotFoundException();
    });
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    Article article = get(id);

    Operation op = new Operation("article", "remove", mapper.valueToTree(article));
    kafka.send(new ProducerRecord<>("shop", op));

    //ist nun im Listener
    //repo.delete();
  }

  @PutMapping("/{id}")
  public void change(@PathVariable String id, @RequestBody Article article) {
    get(id);

    article.setUuid(id);
    repo.save(article);
  }

  @PatchMapping("/{id}")
  public void patch(@PathVariable String id, @RequestBody JsonNode json) {

    Article old = get(id);

    //JSON hat drei Zustände: kein Attribut, null, Wert
    if(json.has(PRICE)) {
      if(json.hasNonNull(PRICE)) {
        old.setPrice(new BigDecimal(json.get(PRICE).asDouble()));
      }
    }

    if(json.has(NAME)) {
      old.setName(json.get(NAME).asText());
    }
    repo.save(old);
  }

}