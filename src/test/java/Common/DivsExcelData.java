package Common;

import com.codeborne.selenide.Configuration;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.*;
import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static Common.DivsCoreData.props;
import static java.util.Collections.*;

public class DivsExcelData {
    private static Logger logger = Logger.getLogger(DivsExcelData.class.getSimpleName());
    public ArrayList<String> companyNames = new ArrayList<String>();
    public ArrayList<String> companyNamesPreviousSelection = new ArrayList<String>();
    public XSSFWorkbook companiesBook; public String comparisonType;
    public final HashMap<String,ArrayList<String>> fieldsSearchCriterias = new LinkedHashMap<>();
    public HashMap<String,Integer> fieldsColumns = new HashMap<>();
    public HashMap<String,String> companyNamesAndTickers = new HashMap<>();
    HashMap<String,String> yieldsAndCompanies = new HashMap<>();

    public ArrayList<String> getAllTickers(XSSFSheet sheet){
        //метод считывает список всех тикеров компаний из файла Excel
        ArrayList<String> tickersFromExcel = new ArrayList<>();
        Cell ticker = findCell(sheet,"Symbol");
        int symbolColumn = ticker.getColumnIndex();
        int firstRowToStart = ticker.getRowIndex()+1;
        int lastRowToEnd = findCell(sheet,"Averages for All").getRowIndex() - 2;
        for (int i = firstRowToStart;i < lastRowToEnd;i++) {
            XSSFRow row = sheet.getRow(i);
            Cell currentTicker = row.getCell(symbolColumn);
            CellType cellType = currentTicker.getCellType();
            if (cellType.name() == "STRING") {
                tickersFromExcel.add(currentTicker.getStringCellValue());
            }
        }
        return tickersFromExcel;
    }

    public void setStrValueToReportCell(Row row, int columnSeqNo, String value){
        Cell cell = row.getCell(columnSeqNo);
        if (cell == null) {
            cell = row.createCell(columnSeqNo);
        }
        cell.setCellValue(value);
    }

    public void setStrValueToReportCell(Row row, int columnSeqNo, Double value){
        Cell cell = row.getCell(columnSeqNo);
        if (cell == null) {
            cell = row.createCell(columnSeqNo);
        }
        cell.setCellValue(value);
    }

    public void setStrValueToReportCell(Row row, int columnSeqNo, int value){
        Cell cell = row.getCell(columnSeqNo);
        if (cell == null) {
            cell = row.createCell(columnSeqNo);
        }
        cell.setCellValue(value);
    }

    public String generateExcelReport(LinkedHashMap<String, Stocks> selection) throws IOException {
        String reportTemplateName = Configuration.reportsFolder + "\\DividendScreenerResultsTemplate.xlsx";
        File excelTemplate = new File(reportTemplateName);
        FileInputStream file = new FileInputStream(excelTemplate);
        XSSFWorkbook book = new XSSFWorkbook(file);
        XSSFSheet sheet = book.getSheet("ScannerResults");
        int rowNum = 7;//определяем порядковый номер первой строки, в которую нужно начинать запись данных
        int columnSeqNo  = 0; //определяем первую незаполненную колонку в первой пустой строке
        //заполняем поля с наименованиями компаний и их базовыми параметрами: тикер, Last Price, Yield
        for (Map.Entry<String, Stocks> entry : selection.entrySet()){
            String ticker = entry.getKey();
            String companyName = entry.getValue().getCompanyName();
            Double lastPrice = entry.getValue().getLastPrice();
            Double yield = entry.getValue().getYield() / 100;
            //заполняем базовые данные по каждой компании сверху вниз
            setStrValueToReportCell(sheet.getRow(rowNum), columnSeqNo,ticker);
            setStrValueToReportCell(sheet.getRow(rowNum), 1,companyName);
            setStrValueToReportCell(sheet.getRow(rowNum), 2,lastPrice);
            setStrValueToReportCell(sheet.getRow(rowNum), 3,yield);
            //заполняем статусы по критериям для компании
            int criteriaStartColumnNum = 4;
            int col = criteriaStartColumnNum;
            for (Map.Entry<String, String> criteria : entry.getValue().criteriaExecutionStatuses.entrySet()){
                String criteriaStatus = criteria.getValue();
                setStrValueToReportCell(sheet.getRow(rowNum), col,criteriaStatus);
                col = col + 1;
            }
            rowNum = rowNum + 1;
        }
        //задаем дату для шаблона имени нового файла отчета
        String newReportName = "";
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        DateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy_hh_mm");
        String newDateStr = dateFormat.format(today);
        int extPos = reportTemplateName.indexOf(".xlsx");
        newReportName = reportTemplateName.substring(0,extPos) + "_" + newDateStr + ".xlsx";
        FileOutputStream fos = new FileOutputStream(newReportName);
        book.write(fos);
        book.close();
        fos.close();
        file.close();
        return newReportName;
    }

    public boolean filterCompanies(int Column, String searchCriteria,String criteriaDescription) throws ParseException {
        ArrayList<String> companyNamesFiltered = new ArrayList<String>();
        DivsCoreData divsCoreData = new DivsCoreData();
        boolean isToContinue = divsCoreData.shouldAnalysisContinue(companyNamesPreviousSelection,companyNames);
        if (!isToContinue) return false;
        logger.log(Level.INFO,criteriaDescription);
        XSSFRow row = null; double expectedValue = 0,currentValue = 0; Date currentDate,expectedDate;
        XSSFSheet sheet = companiesBook.getSheet("All CCC");
        //для каждой компании из списка выбираем только те, которые соответствуют критерию
        for (int i = 0; i < companyNames.size();i++){
            //находим компанию в общем списке
            row = findCompanyRow(sheet,companyNames.get(i));
            //выполняем сравнение фактического показателя компании с критерием отбора
            if (row != null) {
                //считываем значение показателя для найденной компании
                Cell c = row.getCell(Column);
                CellType cellType = c.getCellType();
                if (cellType.name() == "NUMERIC" || cellType.name() == "FORMULA") {
                    //немного разная логика для дат и числовых значений
                    if (searchCriteria.indexOf("/") > 0) {
                        //считываем значение найденной ячейки в формате Дата
                        currentDate = c.getDateCellValue();
                        //конвертируем значение критерия
                        expectedDate = new SimpleDateFormat("dd/MM/yyyy").parse(searchCriteria);
                        //если компания удовлетворяет критерию, добавляем ее в отдельный массив отфильтрованных результатов
                        if (currentDate.compareTo(expectedDate) > 0 ) companyNamesFiltered.add(companyNames.get(i));
                    } else {
                        //пропускаем строки, где в числовых полях n/a
                        String text = ((XSSFCell) c).getRawValue();
                        if (text.contains("n/a")) continue;
                        //считываем значение найденной ячейки в число
                        currentValue = c.getNumericCellValue();
                        //конвертируем значение критерия
                        expectedValue = Double.parseDouble(searchCriteria);
                        //если компания удовлетворяет критерию, добавляем ее в отдельный массив отфильтрованных результатов
                        if (comparisonType == "GREATER_THAN_OR_EQUALS")
                            if (currentValue >= expectedValue) companyNamesFiltered.add(companyNames.get(i));
                        if (comparisonType == "LESSER_THAN")
                            if (currentValue < expectedValue) companyNamesFiltered.add(companyNames.get(i));
                    }
                }
            }
        }
        copyFilteredCompanies(companyNamesFiltered);
        return true;
    }

    public HashMap<String,String> changeExecutionStatus(HashMap<String,String> criteriaExecutionStatuses,String testStatusToChange, String newTestStatus,String sortType){
        String testCode = "";
        LinkedList reverseStatuses = new LinkedList(criteriaExecutionStatuses.entrySet());
        if (sortType.equals("reverse")) reverse(reverseStatuses);
        for (int i=0; i< reverseStatuses.size();i++){
            String test = reverseStatuses.get(i).toString();
            if (test.contains(testStatusToChange)){
                int div = test.indexOf("=");
                testCode = test.substring(0,div);
                break;
            }
        }
        criteriaExecutionStatuses.put(testCode,newTestStatus);
        return criteriaExecutionStatuses;
    }

    public HashMap<String,String> setDefaultExecutionStatus(String testStatus){
        HashMap<String,String> criteriaExecutionStatuses = new LinkedHashMap<>();
        criteriaExecutionStatuses.put("Yrs",testStatus);
        criteriaExecutionStatuses.put("Yield",testStatus);
        criteriaExecutionStatuses.put("Year",testStatus);
        criteriaExecutionStatuses.put("Inc.",testStatus);
        criteriaExecutionStatuses.put("Ex-Div",testStatus);
        criteriaExecutionStatuses.put("Payout",testStatus);
        criteriaExecutionStatuses.put("($Mil)",testStatus);
        criteriaExecutionStatuses.put("P/E",testStatus);
        criteriaExecutionStatuses.put("SDYCheck",testStatus);
        criteriaExecutionStatuses.put("DivCheck",testStatus);
        criteriaExecutionStatuses.put("IncomeCheck",testStatus);
        return criteriaExecutionStatuses;
    }

    public void setSearchCriteria(XSSFSheet sheet){
//        ArrayList<String> Yrs = new ArrayList<String>();
//        ArrayList<String> Yield = new ArrayList<String>();
        ArrayList<String> Payouts = new ArrayList<String>();
        ArrayList<String> mr = new ArrayList<String>();
        ArrayList<String> exDiv = new ArrayList<String>();
        ArrayList<String> EPS = new ArrayList<String>();
        ArrayList<String> MktCap = new ArrayList<String>();
//        ArrayList<String> pe = new ArrayList<String>();
//        Yrs.add("15");
//        Yrs.add("Yrs - компании, которые платят дивиденды 15 и более лет");
//        Yrs.add("GREATER_THAN_OR_EQUALS");
//        Yield.add("2.50");
//        Yield.add("Div.Yield - дивиденды от 2.5% годовых");
//        Yield.add("GREATER_THAN_OR_EQUALS");
        Payouts.add("4");
        Payouts.add("Payouts/Year - частота выплаты дивидендов - не реже 4 раз в год");
        Payouts.add("GREATER_THAN_OR_EQUALS");
        mr.add("2.00");
        mr.add("MR%Inc. - ежегодный прирост дивидендов от 2% в год");
        mr.add("GREATER_THAN_OR_EQUALS");
        exDiv.add(getPrevYear());
        exDiv.add("Last Increased on: Ex-Div - дата последнего повышения дивидендов - не позже, чем год назад");
        exDiv.add("GREATER_THAN_OR_EQUALS");
        EPS.add("70.00");
        EPS.add("EPS%Payout - доля прибыли, направляемая на выплату дивидендов, не более 70%");
        EPS.add("LESSER_THAN");
        MktCap.add("2000.00");
        MktCap.add("MktCap($Mil) - компании с капитализацией свыше 2млрд.долл");
        MktCap.add("GREATER_THAN_OR_EQUALS");
//        pe.add("21.00");
//        pe.add("TTM P/E - срок окупаемости инвестиций в акции компании в годах - для американского рынка не должен превышать 21");
//        pe.add("LESSER_THAN");
        //заполняем хешмеп данными по критериям поиска по полям
//        fieldsSearchCriterias.put("Yrs",Yrs);
//        fieldsSearchCriterias.put("Yield",Yield);
        fieldsSearchCriterias.put("Year",Payouts);
        fieldsSearchCriterias.put("Inc.",mr);
        fieldsSearchCriterias.put("Ex-Div",exDiv);
        fieldsSearchCriterias.put("Payout",EPS);
        fieldsSearchCriterias.put("($Mil)",MktCap);
//        fieldsSearchCriterias.put("P/E",pe);
        //ищем строку с шапкой таблицы - названием полей
        logger.log(Level.INFO, "находим поля, по которым будет проводиться отбор компаний...");
        Cell numberOfYears = findCell(sheet,"Yrs");
        XSSFRow row = (XSSFRow) numberOfYears.getRow();
        for(Cell cell : row) {
            //записываем названия всех полей в массив
            CellType cellType = cell.getCellType();
            if(cellType.name() == "STRING") {
                String fieldName = cell.getStringCellValue();
                //если имя найденного поля входит в список полей для отбора, сохраняем его порядковый номер в отдельный хешмап
                if (fieldsSearchCriterias.keySet().contains(fieldName))
                    fieldsColumns.put(fieldName,cell.getColumnIndex());
            }
        }
        logger.log(Level.INFO, "критерии поиска определены по компаниям считаны");
    }

    public void copyFilteredCompanies(ArrayList<String> companyNamesFiltered){
        //очищаем массив предыдущей выборки
        companyNamesPreviousSelection.clear();
        //копируем в массив предыдущей выборки текущую выборку - список компаний до запуска текущей сессии фильтрации
        companyNamesPreviousSelection = (ArrayList<String>)companyNames.clone();
        //очищаем основной массив
        companyNames.clear();
        //копируем значения из новой выборки в основной - список отобранных компаний в текущей сессии фильтрации
        companyNames = (ArrayList<String>)companyNamesFiltered.clone();
    }

    public String getPrevYear(){
        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        Date prevYear = cal.getTime();
        String lastYear = dateFormat.format(prevYear);
        return lastYear;
    }

    public ArrayList<String> getCompaniesTickersByNames(XSSFSheet sheet, ArrayList<String> names){
        ArrayList<String> tickers = new ArrayList<String>();
        //определяем порядковый номер поля с тикером
        Cell company = findCell(sheet,"Symbol");
        int symbolColumn = company.getColumnIndex();
        //для каждой отобранной компании считываем ее тикер
        for (int i=0; i< names.size(); i++){
            //находим ячейку с уникальным именем компании
            Cell name = findCell(sheet,names.get(i));
            //считываем всю строку с найденной ячейкой
            XSSFRow row = (XSSFRow) name.getRow();
            //считываем тикер в строке
            Cell cell = row.getCell(symbolColumn);
            //добавляем тике в массив
            String tickerValue = cell.getStringCellValue();
            tickers.add(tickerValue);
            //заполняем хешмап названиями компаний и их тикерами - пригодится в самом конце для вывода итоговых отобранных списков
            if (companyNamesAndTickers.size() < names.size())
                companyNamesAndTickers.put(tickerValue,names.get(i));
        }
        return tickers;
    }

    public ArrayList<String> getCompaniesNamesByTickers(XSSFSheet sheet, ArrayList<String> tickers){
        ArrayList<String> names = new ArrayList<String>();
        //определяем порядковый номер поля с тикером
        Cell company = findCell(sheet,"Name");
        int nameColumn = company.getColumnIndex();
        //для каждой отобранной компании считываем ее тикер
        for (int i=0; i< tickers.size(); i++){
            //находим ячейку с уникальным именем компании
            Cell ticker = findCellInColumn(sheet,nameColumn,tickers.get(i));
            //считываем всю строку с найденной ячейкой
            XSSFRow row = (XSSFRow) ticker.getRow();
            //считываем тикер в строке
            Cell cell = row.getCell(nameColumn);
            //добавляем тике в массив
            names.add(cell.getStringCellValue());
        }
        return names;
    }

    public static XSSFRow findCompanyRow(XSSFSheet sheet, String companyName) {
        XSSFRow companyRow = null;
        //задаем границы поиска - первую и последнюю строку списка компаний
        Cell name = findCell(sheet,"Name");
        int nameColumn = name.getColumnIndex();
        int nameFirstRow = name.getRowIndex();
        Cell endOfList = findCell(sheet,"Averages for All");
        int lastRowNumber = endOfList.getRowIndex() - 2;
        //ищем в поле с названиями компаний
        for (int i = nameFirstRow; i < lastRowNumber;i++) {
            //по очереди считываем
            XSSFRow row = sheet.getRow(i);
            //считываем названия в каждой строке в этом поле
            Cell cell = row.getCell(nameColumn);
            CellType cellType = cell.getCellType();
            if (cellType.name() == "STRING") {
                //если название совпадает с искомым, возвращаем всю строку
                if(companyName.equals(cell.getStringCellValue())) {
                    companyRow = row;
                    break;
                }
            }
        }
        return companyRow;
    }

    public void saveFilteredResults(XSSFWorkbook book, File destFile) throws IOException, InvalidFormatException {
        XSSFWorkbook workbookoutput=book;
        //To write your changes to new workbook
        FileOutputStream out = new FileOutputStream(destFile);
        workbookoutput.write(out);
        out.close();
    }

    public void removeSheets(File sourceFile, File destFile) throws IOException {
        FileInputStream fis=new FileInputStream(sourceFile);
        Workbook wb= WorkbookFactory.create(fis);
        for (int i = wb.getNumberOfSheets() - 1; i >= 0; i--) {
            if (!wb.getSheetName(i).contentEquals("All CCC") && !wb.getSheetName(i).contentEquals("Historical")) //This is a place holder. You will insert your logic here to get the sheets that you want.
                wb.removeSheetAt(i); //Just remove the sheets that don't match your criteria in the if statement above
        }
        FileOutputStream fos = new FileOutputStream(destFile);
        wb.write(fos);
        fos.close();
    }

    public XSSFSheet getDivsSheet(File fileName) throws IOException {
        FileInputStream file = new FileInputStream(fileName);
        XSSFWorkbook book = new XSSFWorkbook(file);
        XSSFSheet sheet = book.getSheet("All CCC");
        book.close();
        file.close();
        return sheet;
    }

    public ArrayList<String> getTickersFromManualExcelFile(String sourceFileName, String sheetName, int colSeqNo, int startRowNum) throws IOException {
        //метод извлекает список тикеров из мануального файла портфолио
        ArrayList<String> list = new ArrayList<String>();
        FileInputStream file = new FileInputStream(Configuration.reportsFolder + sourceFileName);
        XSSFWorkbook book = new XSSFWorkbook(file);
        XSSFSheet sheet = book.getSheet(sheetName);
        int countOfRows = sheet.getLastRowNum() - startRowNum;
        for (int i = startRowNum; i< countOfRows; i++){
            Row row = sheet.getRow(i);
            Cell cell = row.getCell(colSeqNo);
            String ticker = cell.getStringCellValue();
            list.add(ticker);
        }
        book.close();
        file.close();
        return list;
    }

    public void getDivsBook(File fileName) throws IOException {
        FileInputStream file = new FileInputStream(fileName);
        companiesBook = new XSSFWorkbook(file);
        file.close();
    }

    public CellAddress findCellAddress(XSSFSheet sheet, String keyword) {
        Iterator<Row> iterator = sheet.iterator();
        int columnNumber = 0;
        int rowNumber = 0;
        CellAddress cellAddress = null;
        while(iterator.hasNext()){
            Row nextRow = iterator.next();
            Iterator<Cell> cellIterator = nextRow.cellIterator();
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                if(cell.getCellType().name() == "STRING"){
                    String currentCellText = cell.getStringCellValue();
                    if (keyword.equals(currentCellText)) {
                        columnNumber=cell.getColumnIndex();
                        rowNumber = cell.getRowIndex();
                        cellAddress = cell.getAddress();
                        break;
                    }
                }
            }
        }
        return cellAddress;
    }

    public static Cell findCell(XSSFSheet sheet, String text) {
        for(Row row : sheet) {
            for(Cell cell : row) {
                CellType cellType = cell.getCellType();
                if(cellType.name() == "STRING") {
                    if(text.equals(cell.getStringCellValue()))
                        return cell;
                }
            }
        }
        return null;
    }

    public static Cell findCellInColumn(XSSFSheet sheet, int columnIndex, String text) {
        for(Row row : sheet) {
            Cell cell = row.getCell(columnIndex);
            CellType cellType = cell.getCellType();
            if(cellType.name() == "STRING") {
                if(text.equals(cell.getStringCellValue()))
                    return cell;
            }
        }
        return null;
    }

    public void getAllCompaniesNames(XSSFSheet sheet){
        //метод считывает список всех названий компаний из файла Excel
        Cell name = findCell(sheet,"Name");
        int nameColumn = name.getColumnIndex();
        int firstRowToStart = name.getRowIndex()+1;
        int lastRowToEnd = findCell(sheet,"Averages for All").getRowIndex() - 2;
        for (int i = firstRowToStart;i < lastRowToEnd;i++) {
            XSSFRow row = sheet.getRow(i);
            Cell currentName = row.getCell(nameColumn);
            CellType cellType = currentName.getCellType();
            if (cellType.name() == "STRING") {
                companyNames.add(currentName.getStringCellValue());
            }
        }
    }

    public void addYieldAndCompanyNames(XSSFRow row, int nameColumn, int yieldColumn){
        //добавляем отобранную компанию в массив
        Cell currentRowName = row.getCell(nameColumn);
        String currentRowNameVal = currentRowName.getStringCellValue();
        Cell currentRowYield = row.getCell(yieldColumn);
        Double yeild = currentRowYield.getNumericCellValue();
        String currentRowYieldVal = Double.toString(yeild);
        yieldsAndCompanies.put(currentRowNameVal,currentRowYieldVal);
    }

    public void setAutoFilter(XSSFSheet sheet, final int column, final String value) throws IOException, InvalidFormatException {
        sheet.setAutoFilter(CellRangeAddress.valueOf("A1:Z1"));
        int currentColumn,currentRow,nameColumn = 0,yieldColumn = 0;
        CTAutoFilter sheetFilter = sheet.getCTWorksheet().getAutoFilter();
        CTFilterColumn filterColumn = sheetFilter.addNewFilterColumn();
        filterColumn.setColId(column);
        CTCustomFilters myCustomFilter=filterColumn.addNewCustomFilters();
        CTCustomFilter myFilter1= myCustomFilter.addNewCustomFilter();
        myFilter1.setOperator(STFilterOperator.GREATER_THAN_OR_EQUAL);
        myFilter1.setVal(value);
        nameColumn = findCell(sheet,"Name").getColumnIndex();
        yieldColumn = findCell(sheet,"Yield").getColumnIndex();
        // We have to apply the filter ourselves by hiding the rows:
        for (Row row : sheet) {
            for (Cell c : row) {
                currentRow = c.getRowIndex();
                currentColumn = c.getColumnIndex();
                if (currentColumn == column) {
                    CellType cellType = c.getCellType();
                    if (cellType.name() == "STRING" || cellType.name() == "BLANK") {
                        String currentValue = c.getStringCellValue();
                        if (currentValue == value) {
                            addYieldAndCompanyNames((XSSFRow) row,nameColumn,yieldColumn);
                            break;
                        }
                    }
                    if (cellType.name() == "NUMERIC" || cellType.name() == "FORMULA") {
                        double currentValue = c.getNumericCellValue();
                        double expectedValue = Double.parseDouble(value);
                        if (currentValue >= expectedValue) {
                            addYieldAndCompanyNames((XSSFRow) row,nameColumn,yieldColumn);
                            break;
                        }
                    }
                }
            }
        }
        Set<Map.Entry<String, String>> entries = yieldsAndCompanies.entrySet();
        // Now let's sort HashMap by keys first // all you need to do is create a TreeMap with mappings of HashMap
        // TreeMap keeps all entries in sorted order
        TreeMap<String, String> sorted = new TreeMap<>(yieldsAndCompanies);
        Set<Map.Entry<String, String>> mappings = sorted.entrySet();
        // Now let's sort the HashMap by values
        // there is no direct way to sort HashMap by values but you
        // can do this by writing your own comparator, which takes
        // Map.Entry object and arrange them in order increasing
        // or decreasing by values.
        Comparator<Map.Entry<String, String>> valueComparator = new Comparator<Map.Entry<String,String>>() {
            @Override public int compare(Map.Entry<String, String> e1, Map.Entry<String, String> e2) {
                String v1 = e1.getValue();
                String v2 = e2.getValue(); return v1.compareTo(v2);
            }
        }; // Sort method needs a List, so let's first convert Set to List in Java
        List<Map.Entry<String, String>> listOfEntries = new ArrayList<Map.Entry<String, String>>(entries);
        // sorting HashMap by values using comparator
        Collections.sort(listOfEntries, valueComparator);
        Collections.reverse(listOfEntries);
        LinkedHashMap<String, String> sortedByValue = new LinkedHashMap<String, String>(listOfEntries.size());
        // copying entries from List to Map
        for(Map.Entry<String, String> entry : listOfEntries){
            companyNames.add(entry.getKey());
            sortedByValue.put(entry.getKey(), entry.getValue());
        }
    }

    public XSSFSheet removeRows(XSSFSheet sheet, int startRow,int endRow){
        for (int i=startRow; i< endRow;i++){
            XSSFRow rowToRemove = sheet.getRow(i);
            sheet.removeRow(rowToRemove);
        }
        return sheet;
    }

    public XSSFSheet removeRowsReverse(XSSFSheet sheet, int startRow,int endRow){
        for (int i=endRow; i > startRow;i--){
            XSSFRow rowToRemove = sheet.getRow(i);
            sheet.removeRow(rowToRemove);
        }
        return sheet;
    }
}
