import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TagExtractor extends JFrame {

    private final JLabel textFileLabel = new JLabel("No text file selected");
    private final JLabel stopFileLabel = new JLabel("No stop-words file selected");
    private final JTextArea outputArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar();

    private File textFile = null;
    private File stopWordsFile = null;
    private Map<String, Integer> lastFrequencyMap = null;

    public TagExtractor() {
        super("Tag Extractor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        add(topPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        JButton chooseTextBtn = new JButton("Choose Text File...");
        JButton chooseStopBtn = new JButton("Choose Stop Words File...");
        JButton extractBtn = new JButton("Extract Tags");
        JButton saveBtn = new JButton("Save Output...");

        chooseTextBtn.addActionListener(this::onChooseText);
        chooseStopBtn.addActionListener(this::onChooseStop);
        extractBtn.addActionListener(this::onExtract);
        saveBtn.addActionListener(this::onSave);

        buttonPanel.add(chooseTextBtn);
        buttonPanel.add(chooseStopBtn);
        buttonPanel.add(extractBtn);
        buttonPanel.add(saveBtn);

        JPanel labels = new JPanel(new GridLayout(2, 1));
        labels.add(textFileLabel);
        labels.add(stopFileLabel);

        topPanel.add(buttonPanel, BorderLayout.NORTH);
        topPanel.add(labels, BorderLayout.SOUTH);

        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(outputArea);
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        progressBar.setStringPainted(true);
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setValue(0);
        bottom.add(progressBar, BorderLayout.NORTH);

        JTextArea summary = new JTextArea();
        summary.setEditable(false);
        summary.setRows(2);
        summary.setBackground(getBackground());
        summary.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        summary.setText("Instructions: choose both files, then click Extract Tags. ");
        bottom.add(summary, BorderLayout.SOUTH);

        add(bottom, BorderLayout.SOUTH);
    }

    private void onChooseText(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt", "text"));
        int rc = chooser.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            textFile = chooser.getSelectedFile();
            textFileLabel.setText("Text file: " + textFile.getAbsolutePath());
            outputArea.append("Selected text file: " + textFile.getName() + "\n");
        }
    }

    private void onChooseStop(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt", "text"));
        int rc = chooser.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            stopWordsFile = chooser.getSelectedFile();
            stopFileLabel.setText("Stop words file: " + stopWordsFile.getAbsolutePath());
            outputArea.append("Selected stop words file: " + stopWordsFile.getName() + "\n");
        }
    }

    private void onExtract(ActionEvent e) {
        if (textFile == null) {
            JOptionPane.showMessageDialog(this, "Please choose a text file first.", "No Text File", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (stopWordsFile == null) {
            JOptionPane.showMessageDialog(this, "Please choose a stop words file first.", "No Stop Words File", JOptionPane.WARNING_MESSAGE);
            return;
        }
        outputArea.append("\nStarting extraction...\n");
        progressBar.setValue(0);

        SwingWorker<Map<String, Integer>, Integer> worker = new SwingWorker<>() {
            @Override
            protected Map<String, Integer> doInBackground() throws Exception {
                Set<String> stopWords = loadStopWords(stopWordsFile);
                publish(10);
                Map<String, Integer> freq = new HashMap<>();

                long totalLines = countLines(textFile);
                long processedLines = 0;

                try (BufferedReader br = Files.newBufferedReader(textFile.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        processedLines++;
                        String cleaned = line.replaceAll("[^A-Za-z]", " ").toLowerCase();
                        if (!cleaned.isBlank()) {
                            String[] tokens = cleaned.split("\\s+");
                            for (String w : tokens) {
                                if (w.isEmpty()) continue;
                                if (stopWords.contains(w)) continue;
                                freq.put(w, freq.getOrDefault(w, 0) + 1);
                            }
                        }

                        if (processedLines % 200 == 0 || processedLines == totalLines) {
                            int percent = totalLines == 0 ? 50 : (int) Math.min(90, (processedLines * 80) / (double) totalLines + 10);
                            publish(percent);
                        }
                    }
                }
                publish(95);
                return freq;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int last = chunks.get(chunks.size() - 1);
                progressBar.setValue(last);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Integer> freq = get();
                    lastFrequencyMap = freq;
                    displayFrequencies(freq);
                    progressBar.setValue(100);
                    outputArea.append("\nExtraction complete. Found " + freq.size() + " unique tags (after stop-word filtering).\n");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(TagExtractor.this, "Error during extraction: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                    progressBar.setValue(0);
                }
            }
        };

        worker.execute();
    }

    private void onSave(ActionEvent e) {
        if (lastFrequencyMap == null || lastFrequencyMap.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No extracted data available. Run extraction first.", "Nothing to Save", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setSelectedFile(new File("tags_output.txt"));
        int rc = chooser.showSaveDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            File out = chooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
                pw.println("Tags frequency output for: " + (textFile != null ? textFile.getName() : "unknown"));
                pw.println("Stop words file: " + (stopWordsFile != null ? stopWordsFile.getName() : "none"));
                pw.println("Generated: " + new Date());
                pw.println("--------------------------------------------------");
                List<Map.Entry<String, Integer>> entries = new ArrayList<>(lastFrequencyMap.entrySet());
                entries.sort((a, b) -> {
                    int cmp = b.getValue().compareTo(a.getValue());
                    return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
                });

                for (Map.Entry<String, Integer> en : entries) {
                    pw.printf("%-20s : %d%n", en.getKey(), en.getValue());
                }
                JOptionPane.showMessageDialog(this, "Saved successfully to: " + out.getAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private Set<String> loadStopWords(File file) throws IOException {
        Set<String> stop = new HashSet<>();
        try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String l;
            while ((l = br.readLine()) != null) {
                String w = l.trim().toLowerCase();
                if (!w.isEmpty()) stop.add(w);
            }
        }
        return stop;
    }

    private void displayFrequencies(Map<String, Integer> map) {
        outputArea.append("\n--- Tags (sorted by frequency desc) ---\n");
        List<Map.Entry<String, Integer>> list = map.entrySet()
                .stream()
                .sorted((a, b) -> {
                    int c = b.getValue().compareTo(a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .collect(Collectors.toList());

        int topN = Math.min(30, list.size());
        outputArea.append(String.format("Top %d tags:\n", topN));
        for (int i = 0; i < topN; i++) {
            Map.Entry<String, Integer> e = list.get(i);
            outputArea.append(String.format("%2d) %-15s : %d%n", i + 1, e.getKey(), e.getValue()));
        }

        outputArea.append("\nFull list:\n");
        for (Map.Entry<String, Integer> e : list) {
            outputArea.append(String.format("%-20s : %d%n", e.getKey(), e.getValue()));
        }
    }

    private long countLines(File f) {
        try (BufferedReader br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            long c = 0;
            while (br.readLine() != null) c++;
            return c;
        } catch (IOException ex) {
            return 0;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TagExtractor t = new TagExtractor();
            t.setVisible(true);
        });
    }
}
