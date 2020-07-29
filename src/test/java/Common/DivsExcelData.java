package Common;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DivsExcelData {
    private static Logger logger = Logger.getLogger(DivsExcelData.class.getSimpleName());
    public ArrayList<String> companyNames = new ArrayList<String>();
    public XSSFWorkbook companiesBook;
    public String comparisonType;

    public ArrayList<String> filterCompanies(int Column, String searchCriteria) throws ParseException {
        ArrayList<String> companyNamesFiltered = new ArrayList<String>();
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
        //очищаем исходный массив
        companyNames.clear();
        //копируем значения из отфильтрованного массива в исходный
        companyNames = (ArrayList<String>)companyNamesFiltered.clone();
        return companyNames;
    }

    public ArrayList<String> getCompaniesTickersByNames(XSSFSheet sheet){
        ArrayList<String> tickers = new ArrayList<String>();
        //определяем порядковый номер поля с тикером
        Cell ticker = findCell(sheet,"Symbol");
        int symbolColumn = ticker.getColumnIndex();
        //для каждой отобранной компании считываем ее тикер
        for (int i=0; i< companyNames.size(); i++){
            //находим ячейку с уникальным именем компании
            Cell name = findCell(sheet,companyNames.get(i));
            //считываем всю строку с найденной ячейкой
            XSSFRow row = (XSSFRow) name.getRow();
            //считываем тикер в строке
            Cell cell = row.getCell(symbolColumn);
            //добавляем тике в массив
            tickers.add(cell.getStringCellValue());
        }
        return tickers;
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

    public void saveCompanyNames(XSSFRow row, int nameColumn ){
        //добавляем отобранную компанию в массив
        Cell tickerCurrentRow = row.getCell(nameColumn);
        String tickerCurrentRowVal = tickerCurrentRow.getStringCellValue();
        companyNames.add(tickerCurrentRowVal);
    }

    public void setAutoFilter(XSSFSheet sheet, final int column, final String value) throws IOException, InvalidFormatException {
        sheet.setAutoFilter(CellRangeAddress.valueOf("A1:Z1"));
        int currentColumn,currentRow,nameColumn = 0;
        CTAutoFilter sheetFilter = sheet.getCTWorksheet().getAutoFilter();
        CTFilterColumn filterColumn = sheetFilter.addNewFilterColumn();
        filterColumn.setColId(column);
        CTCustomFilters myCustomFilter=filterColumn.addNewCustomFilters();
        CTCustomFilter myFilter1= myCustomFilter.addNewCustomFilter();
        myFilter1.setOperator(STFilterOperator.GREATER_THAN_OR_EQUAL);
        myFilter1.setVal(value);
        nameColumn = findCell(sheet,"Name").getColumnIndex();
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
                            saveCompanyNames((XSSFRow) row,nameColumn);
                            break;
                        }
                    }
                    if (cellType.name() == "NUMERIC" || cellType.name() == "FORMULA") {
                        double currentValue = c.getNumericCellValue();
                        double expectedValue = Double.parseDouble(value);
                        if (currentValue >= expectedValue) {
                            saveCompanyNames((XSSFRow) row,nameColumn);
                            break;
                        }
                    }
                }
            }
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
